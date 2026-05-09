package com.analytics.portfolio.utils;

public class PortfolioUtils {

    public static String mapToLS(String searchableTicker) {

        return switch (searchableTicker) {
            case "NVG.PT" -> "NVG.LS";
            case "SON.PT" -> "SON.LS";
            case "NOS.PT" -> "NOS.LS";
            case "RENE.PT" -> "RENE.LS";
            case "JMT.PT" -> "JMT.LS";
            case "EDP.PT" -> "EDP.LS";
            case "COR.PT" -> "COR.LS";
            case "EGLN.UK" -> "EGLN.L";
            case "ITKY.NL" -> "ITKY";
            case "AAPL.US" -> "AAPL";
            case "NVDA.US" -> "NVDA";

            default -> searchableTicker;
        };
    }

    public static String mapToPT(String searchableTicker) {

        return switch (searchableTicker) {
            case "NVG.LS" -> "NVG.PT";
            case "SON.LS" -> "SON.PT";
            case "NOS.LS" -> "NOS.PT";
            case "RENE.LS" -> "RENE.PT";
            case "JMT.LS" -> "JMT.PT";
            case "EDP.LS" -> "EDP.PT";
            case "COR.LS" -> "COR.PT";
            case "EGLN.L" -> "EGLN.UK";
            case "ITKY" -> "ITKY.NL";
            case "AAPL" -> "AAPL.US";
            case "NVDA" -> "NVDA.US";

            default -> searchableTicker;
        };
    }

}
