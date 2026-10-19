@echo off
chcp 65001 >nul
title SportCenter Backend

REM ============================================================
REM  NOTE RUN - SWP391 Sport Center (Backend)
REM  Dat file nay vao thu muc "backend" (cung cho voi pom.xml)
REM  Sau do chi can double-click file nay de chay.
REM ============================================================

REM ----- Tai khoan database (SQL Server) -----
set SPRING_DATASOURCE_USERNAME=sa
set SPRING_DATASOURCE_PASSWORD=12345

REM ----- JWT secret key (64 ky tu, dap ung HS256 >= 32 ky tu) -----
set JWT_SECRET=a3f9c1e7b2d84f60915c7e3ab8d2046f1e9b7c53d8a40f62b19e5c7d3a8f0412

REM ----- Thanh toan online GIA LAP (chi dung de demo/test) -----
set SPRING_PROFILES_ACTIVE=demo
set APP_PAYMENT_MOCKGATEWAY_ENABLED=true
set APP_PAYMENT_CALLBACKSECRET=demo-payment-secret-7f3a9c1e5b2d4860a1c3e5f70912ab

REM ----- Chay ung dung -----
cd /d "%~dp0"

if exist mvnw.cmd (
    call mvnw.cmd spring-boot:run
) else (
    call mvn spring-boot:run
)

pause