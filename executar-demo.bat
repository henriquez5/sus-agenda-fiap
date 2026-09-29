@echo off
cd /d "%~dp0"
echo SUS Agenda - demonstracao local, dados em memoria.
echo Swagger: http://localhost:8080/swagger-ui.html
echo Usuario: operador / Senha: operador-demo
java -jar bin\sus-agenda.jar --spring.profiles.active=demo
pause
