# Trading Microservices Simulation Platform

This project is a simulation tool for financial markets, developed using the
microservices architecture. It enables communication between broker clients and
market services via FIX messaging while providing external clients with
JSON-based interfaces. The system is designed for modularity, scalability, and
efficient message handling.

## Owerview

### Features
- **Microservices Architecture**: Each component operates independently, communicating through defined TCP interfaces using FIX protocol.
- **Docker Deployment**: Fully configured for containerization, simplifying deployment and scalability.
- **Service Discovery and Load Balancing**: Uses Eureka to manage service registration and traffic distribution to achieve common gateway for the clients and load balancing.
- **JSON Interfaces for Clients via WebSocket**:
    - Brokers send *Buy* and *Sell* orders in JSON format.
    - Responses include *Executed*, *Rejected*, and the up-to-date exchanges
      states.
- **Spring**: Built using a spring platform, that allows convenient
  configuration of each service as well as possibilities for potential,
  modification & feature addition.
    - Services are built entirely around *Spring WebFlux* framework, ensuring
      asynchronous and efficient request processing with *ReactorNetty* web
      server.

### Core Components
1. **Router**:
    - Dispatches messages between brokers and exchanges.
    - Aggregates current states of all available exchanges.
    - Maintains a routing tables for registered services using unique IDs.
    - Performs validation and forwards messages based on routing rules.
2. **Broker**:
    - Provides client's WebSocket interface.
    - Sends orders to the router (*Buy* or *Sell*).
    - Processes execution results returned by the exchange.
3. **Exchange**:
    - Handles incoming orders and attempts to execute them.
    - Responds with execution outcomes or rejections.
    - Persists all changes into DB.

### Additional Components

1. **Eureka server**
2. **Gateway service**

## How to use

The app is a multi-module maven project, alowing one-command compilation for all
modules. It is containerized and can be deployed using Docker.
A `docker-compose.yml` file is included to simplify setup.

1. Compile: `mvn clean package -DskipTests`
2. Create `.env` file:

```
DB_NAME=***
DB_USERNAME=***
DB_PASSWORD=***
DB_ADMIN_PASSWORD=***

DB_HOST=***
DB_PORT=***
EUREKA_HOST=***
EUREKA_PORT=***
ROUTER_HOST=***
ROUTER_EXCHANGE_PORT=***
ROUTER_BROKER_PORT=***
GATEWAY_PORT=***
EUREKA_URI=***

```

3. Deploy: `docker compose up`
4. Access

- Client's gateway: ``
- Eureka web dashboard: ``

