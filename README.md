# Trading Microservices Simulation Platform

This project is a simulation tool for financial markets, developed using the microservices' architecture. It enables communication between broker clients and market services via FIX messaging while providing external clients with JSON-based interfaces. The system is designed for modularity, scalability, and efficient message handling.

### Features
- **Microservices Architecture**: Each component operates independently, communicating through defined TCP interfaces using FIX protocol.
- **Docker Deployment**: Fully configured for containerization, simplifying deployment and scalability.
- **Service Discovery and Load Balancing**: Uses Eureka to manage service registration and traffic distribution to achieve common gateway for the clients and load balancing.
- **JSON Interfaces for Clients via WebSocket**:
    - Brokers send *Buy* and *Sell* orders in JSON format.
    - Responses include *Executed*, *Rejected*, and the up-to-date exchanges states.
- **Spring**: Built using a spring platform, that allows convenient configuration of each service as well as possibilities for potential feature addition.
  - Services are built entirely around *Spring WebFlux* framework, ensuring reactive asynchronous and efficient request processing.  

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
3. **Market**:
    - Handles incoming orders and attempts to execute them.
    - Responds with execution outcomes or rejections.
    - Persists all changes into DB.

### Additional Components
1. **Eureka server**:
2. **Gateway service**

### Deployment
The system is containerized and can be deployed using Docker. A `docker-compose.yml` file is included to simplify setup.
1. Build: ``
2. Run: ``
