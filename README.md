# WoT - Project 3: Simulating the Internet of Things through the Web of Things

## Description

This project involved developing a REST-based architecture to simulate a Web of Things (WoT) scenario using Spring Boot and Maven. The system includes a lamp, a thermostat, and a motion sensor that interact through automated rules to control lighting and temperature according to detected movement and elapsed time. An additional meter-circuit breaker was developed to calculate the system's instantaneous and cumulative electricity consumption and to switch the power supply of all devices on or off. The project focused on implementing communication between connected objects and extending the existing architecture with new functionalities.

## Requirements

- JDK 17 or later (tested with JDK 21)
- Maven 3.9
- bash or Powershell
- curl for the tests

## Run

```bash
./run.sh                      # Ctrl+C to stop
```
```powershell
.\run.ps1                     # then .\stop.ps1
```

Or one service per terminal: `mvn -pl gateway spring-boot:run`, then `mvn -pl thing-lamp spring-boot:run`.

Dashboard: http://localhost:8080/ (token `operator-secret`).

## Tests

```bash
./check-routes.sh
```

## Examples

```bash
T="Authorization: Bearer operator-secret"
curl -H "$T" http://localhost:8080/things
curl -H "$T" http://localhost:8080/things/lamp
curl -N "http://localhost:8080/events/stream?token=operator-secret"
curl -X POST http://localhost:8082/actions/toggle
curl -X PUT -H "Content-Type: application/json" -d '{"value":true}' http://localhost:8082/properties/on
```

## Work completed

- Things creation and implementation
- All rules and logic of objects
- Proxy of the gateway
- Dashboard
- Security with token
- Liveness checking of things
- OpenAPI specification (gateway.yaml)
- Report
- Demo video
- Installation guide (README)

## See more
- [report](WoT%20-%20Project%203%20Report.pdf)
- [demo video](https://www.youtube.com/watch?v=s5oXix25XVY)
