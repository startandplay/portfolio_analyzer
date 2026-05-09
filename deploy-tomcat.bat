@echo off
REM ============================================
REM Portfolio Analytics - Tomcat Deploy Script
REM ============================================

setlocal enabledelayedexpansion

REM Configurações
set TOMCAT_HOME=C:\bin\apache-tomcat\apache-tomcat-10
set APP_NAME=portfolio-analytics
set WAR_FILE=target\%APP_NAME%.war

echo ========================================
echo   Portfolio Analytics - Deploy Script
echo ========================================
echo.

REM Verificar se está no diretório correto
if not exist "pom.xml" (
    echo [ERRO] pom.xml nao encontrado!
    echo Execute este script no diretorio raiz do projeto
    pause
    exit /b 1
)

REM Passo 1: Limpar e compilar
echo [1/5] Compilando projeto...
call mvn clean package -DskipTests

if not exist "%WAR_FILE%" (
    echo [ERRO] WAR nao foi gerado!
    pause
    exit /b 1
)

echo [OK] WAR compilado: %WAR_FILE%
echo.

REM Passo 2: Parar Tomcat
echo [2/5] Parando Tomcat...
call %TOMCAT_HOME%\bin\shutdown.bat
timeout /t 5 /nobreak >nul

REM Passo 3: Backup do WAR antigo
if exist "%TOMCAT_HOME%\webapps\%APP_NAME%.war" (
    echo [3/5] Criando backup do WAR antigo...
    move /y "%TOMCAT_HOME%\webapps\%APP_NAME%.war" "%TOMCAT_HOME%\webapps\%APP_NAME%.war.backup"
    rmdir /s /q "%TOMCAT_HOME%\webapps\%APP_NAME%" 2>nul
) else (
    echo [3/5] Sem WAR antigo para backup
)
echo.

REM Passo 4: Copiar novo WAR
echo [4/5] Copiando novo WAR para Tomcat...
copy /y "%WAR_FILE%" "%TOMCAT_HOME%\webapps\"
echo [OK] WAR copiado para: %TOMCAT_HOME%\webapps\%APP_NAME%.war
echo.

REM Passo 5: Iniciar Tomcat
echo [5/5] Iniciando Tomcat...
start "" "%TOMCAT_HOME%\bin\startup.bat"

echo.
echo ========================================
echo   Deploy concluido com sucesso!
echo ========================================
echo.
echo Aguarde aproximadamente 10 segundos para a aplicacao iniciar
echo.
echo URLs:
echo   - API Base:   http://localhost:8080/%APP_NAME%/api/
echo   - Swagger UI: http://localhost:8080/%APP_NAME%/swagger-ui.html
echo   - H2 Console: http://localhost:8080/%APP_NAME%/h2-console
echo.
echo Logs:
echo   type %TOMCAT_HOME%\logs\catalina.out
echo.
echo Deploy finalizado!
echo.
pause