-- Portfolio Analytics Database Initialization Script

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_transactions_portfolio_date 
  ON transactions(portfolio_id, transaction_date DESC);

CREATE INDEX IF NOT EXISTS idx_positions_portfolio_asset 
  ON positions(portfolio_id, asset_id);

CREATE INDEX IF NOT EXISTS idx_dividends_portfolio_date 
  ON dividends(portfolio_id, payment_date DESC);

CREATE INDEX IF NOT EXISTS idx_price_history_asset_date 
  ON price_history(asset_id, price_date DESC);

CREATE INDEX IF NOT EXISTS idx_assets_symbol 
  ON assets(symbol);

-- Create view for portfolio summary
CREATE OR REPLACE VIEW portfolio_summary AS
SELECT 
    p.id,
    p.name,
    p.currency,
    COUNT(DISTINCT pos.id) as number_of_positions,
    SUM(pos.current_value) as total_value,
    SUM(pos.total_invested) as total_invested,
    SUM(pos.unrealized_pl) as total_unrealized_pl,
    (SELECT SUM(net_amount) FROM dividends WHERE portfolio_id = p.id) as total_dividends
FROM portfolios p
LEFT JOIN positions pos ON pos.portfolio_id = p.id
GROUP BY p.id, p.name, p.currency;

-- Create view for asset performance
CREATE OR REPLACE VIEW asset_performance AS
SELECT 
    a.id,
    a.symbol,
    a.name,
    a.type,
    COUNT(DISTINCT t.id) as transaction_count,
    SUM(CASE WHEN t.type = 'BUY' THEN t.quantity ELSE 0 END) as total_bought,
    SUM(CASE WHEN t.type = 'SELL' THEN t.quantity ELSE 0 END) as total_sold,
    SUM(CASE WHEN t.type = 'BUY' THEN t.total_amount ELSE 0 END) as total_invested,
    SUM(t.fees) as total_fees
FROM assets a
LEFT JOIN transactions t ON t.asset_id = a.id
GROUP BY a.id, a.symbol, a.name, a.type;

-- Create function to calculate portfolio return
CREATE OR REPLACE FUNCTION calculate_portfolio_return(p_portfolio_id BIGINT)
RETURNS TABLE (
    total_return NUMERIC,
    return_percentage NUMERIC,
    days_invested INTEGER
) AS $$
DECLARE
    v_total_invested NUMERIC;
    v_current_value NUMERIC;
    v_total_dividends NUMERIC;
    v_first_transaction_date TIMESTAMP;
BEGIN
    -- Get total invested
    SELECT COALESCE(SUM(total_invested), 0) INTO v_total_invested
    FROM positions
    WHERE portfolio_id = p_portfolio_id;
    
    -- Get current value
    SELECT COALESCE(SUM(current_value), 0) INTO v_current_value
    FROM positions
    WHERE portfolio_id = p_portfolio_id;
    
    -- Get total dividends
    SELECT COALESCE(SUM(net_amount), 0) INTO v_total_dividends
    FROM dividends
    WHERE portfolio_id = p_portfolio_id;
    
    -- Get first transaction date
    SELECT MIN(transaction_date) INTO v_first_transaction_date
    FROM transactions
    WHERE portfolio_id = p_portfolio_id AND type = 'BUY';
    
    -- Calculate return
    RETURN QUERY SELECT 
        (v_current_value - v_total_invested + v_total_dividends) as total_return,
        CASE 
            WHEN v_total_invested > 0 THEN 
                ((v_current_value - v_total_invested + v_total_dividends) / v_total_invested * 100)
            ELSE 0
        END as return_percentage,
        CASE 
            WHEN v_first_transaction_date IS NOT NULL THEN 
                EXTRACT(DAY FROM (NOW() - v_first_transaction_date))::INTEGER
            ELSE 0
        END as days_invested;
END;
$$ LANGUAGE plpgsql;

-- Insert sample data (optional, comment out if not needed)
-- INSERT INTO portfolios (name, description, currency, initial_capital, created_at, updated_at)
-- VALUES ('Demo Portfolio', 'Sample portfolio for testing', 'EUR', 10000.00, NOW(), NOW());

-- Create trigger to update position metrics when price changes
CREATE OR REPLACE FUNCTION update_position_metrics()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.current_price IS NOT NULL AND NEW.quantity IS NOT NULL THEN
        NEW.current_value := NEW.current_price * NEW.quantity;
        NEW.unrealized_pl := NEW.current_value - NEW.total_invested;
        
        IF NEW.total_invested > 0 THEN
            NEW.unrealized_pl_percentage := (NEW.unrealized_pl / NEW.total_invested) * 100;
        END IF;
    END IF;
    
    NEW.last_updated := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Attach trigger (will be created when tables exist)
-- CREATE TRIGGER trigger_update_position_metrics
-- BEFORE INSERT OR UPDATE ON positions
-- FOR EACH ROW EXECUTE FUNCTION update_position_metrics();

COMMENT ON TABLE portfolios IS 'Main portfolio table containing user portfolios';
COMMENT ON TABLE assets IS 'Financial assets (stocks, crypto, etc.)';
COMMENT ON TABLE positions IS 'Current positions in portfolios';
COMMENT ON TABLE transactions IS 'All buy/sell transactions';
COMMENT ON TABLE dividends IS 'Dividend payments received';
COMMENT ON TABLE price_history IS 'Historical price data for assets';
