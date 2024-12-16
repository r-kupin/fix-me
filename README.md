# Trading Microservices Simulation Platform

This project is a simulation tool for financial markets, developed using the
microservices architecture. It enables communication between broker clients and
market services via FIX messaging while providing external clients with
JSON-based interfaces. The system is designed for modularity, scalability, and
efficient message handling.

## Overview

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

## How to

### Setup

The app is a multi-module maven project, alowing one-command compilation for all
modules. It is containerized and can be deployed using Docker.
A `docker-compose.yml` file is included to simplify setup.

1. Compile: `mvn clean package`
2. Create `.env` file:

```env
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
4. Access:
    - Client's gateway: `ws://localhost:8080/ws/requests`
    - Eureka web dashboard: `http://localhost:8761`

### Trade

#### StockState
Upon establishing connection client is welcomed with JSON a message containing current stocks state:
```json
{
    "stocks": {
        "E00000": {
            "TEST2": 2,
            "TEST1": 1
        },
        "E00001": {
            "TEST3": 3,
            "TEST4": 4
        }
    }
}
```

where:
- **`E0000X`**: is a exchange unique id
- **`"TEST3": 3`**: instrument to be traded and amount available

#### Trading
Client's gateway expects clients to send trading requests in a JSON format:

```json
{
    "target": "E00000",
    "instrument": "TEST2",
    "action": "sell",
    "amount": 100
}
```

Upon receiving, service responds with acknowledgement message, such as `Trading request sent` if request is correct an can be sent, or `Trading request not sent:` - followed by an explicit explanation why. If request was sent - client will receive a trading response message:
```json
{
    "sender": "E00000",
    "instrument": "TEST2",
    "action": "sell",
    "ordStatus": "filled",
    "rejectionReason": null,
    "amount": 100
}
```
If trading request will be impossible to fulfill (for example, requested quantity isn't available), the response will look like this:
```json
{
    "sender": "E00000",
    "instrument": "TEST2",
    "action": "buy",
    "ordStatus": "rejected",
    "rejectionReason": "Target exchange doesn't possess requested quantity",
    "amount": 1000
}
```
Each successful request made by any client modifies the state of the market, and each market state modification is broadcast-ed to all connected clients via stock state message.

## Project structure

### Broker-service

#### Configuration

Used to specify key variables:
- **`ROUTER_HOST`** and **`ROUTER_BROKER_PORT`**: to access router
- **`SERVER_LISTEN_PORT`** and **`SERVER_LISTEN_ADDR`**: for clients to connect
- **`EUREKA_URI`**: eureka server's address
#### Client-to-Service communication
##### Configuration
Happens via `websocket` protocol. It is configured via :
- **`SERVER_LISTEN_PORT`**  and **`SERVER_LISTEN_ADDR`** environment variables that specify host and port for `ReactorNetty` web server
- Handler path, set in the `WebSocketConfig`. As long service intends the use of a single `WebSocketHandler` - there is a single hard-coded path to it: `/ws/requests`.

##### Implementation
`TradingWebSocketHandler` is the only `WebSocketHandler` implemented and it is responsible for direct communication with clients.

Upon initialization, it tries to establish connection to the `router-service`, on which it utterly depends. Then, regardless of the result, it starts asynchronously processing emissions from **client publishers**:
- **`onConnection`**: when new client connects, it should be welcomed with a [stock state message](#StockState). In order to compose it,  `TradingService` should be fetched for a current state, as it directly obtains it from the router via [FixStockStateReport](#FixStockStateReport) messages. If connection to the router is not established (initial attempt failed) - service tries to reconnect.
- **`onClientMessage`**: when client sends [trading request](#Trading), it get's parsed and transformed into [FixRequest](#FixRequest) which is handled to `TradingService` to be sent to the router. Immediately after that, client receives a short message whether request was sent or not, if not - then, why?  If connection to the router is not established  - service tries to reconnect.

#### Service-to-Client communication
In order for service to be able to asynchronously reply to client's input - e.g. deliver **stock updates** and **trading responses** - Spring's `ApplicationEvent`s are employed in conjunction with [Project Reactor's `FluxSink`](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/FluxSink.html) and java's own `Executor`.

##### Events
In order to avoid usage of vanilla `ApplicationEvent`, the custom `InputEvent<E>` is created, where `E` is supposed to be a router-sent message, that should be delivered to the client. 

Events are published by `TradingService` using spring's `ApplicationEventPublisher`
##### Listeners
In order to embed events processing into the main reactive flow, `InputEventPublisher<E extends InputEvent>` is implemented. One instance of `InputEventPublisher` for each type of events are declared as a `@Bean` in the `EventConfig` class.

###### InputEventPublisher
While being an `ApplicationListener` it is also a `Consumer<FluxSink>`, which means it can handle events with `onApplicationEvent` by adding them to it's `BlockingQueue`, that in turn is drained by the `Executor`, that in the infinite cycle tries to take all elements from the `BlockingQueue` and put them into `FluxSink`.

When `BlockingQueue` get's empty, `Executor` get's blocked, until in the main thread new events are added via `onApplicationEvent`.
##### Event processing
###### Event publishing
Using `ApplicationEventPublisher` and `InputEvent` allows `TradingService` to act as a publisher. Whenever a new message arrives over TCP, `TradingService` publishes an event that the `InputEventPublisher` component receives. This way, `TradingService` is decoupled from `TradingWebSocketHandler`, avoiding circular dependencies and providing decent flexibility.
###### Event handling
`ApplicationEventPublisher` subscribes to `InputEvent`s by extending `ApplicationListener` and is used as a `Consumer<FluxSink<InputEvent>>` for creating a `Flux`:
- **`BlockingQueue`**: Stores incoming events, allowing `Flux.create`  to drain them as they arrive.
- **Infinite loop with `FluxSink`**: This ensures that as soon as a message is available, it is pushed to the `FluxSink`, which then emits it to the subscriber.
###### Event consuming
`InputEventPublisher`s are injected in the `TradingWebSocketHandler`, that creates `Flux` around each of them. `Flux.create(...).share()`  ensures that `Flux` is multicast, meaning all subscribers will receive the same messages in real-time.

Events are then transformed into JSON `WebSocketMessage` publishers **`onTradeResponse`** and **`onStateUpdate`**
to which `ReactorNetty` is subscribed via `WebSocketSession`'s `send()` method, that finally sends messages to clients over `WebSocket` protocol. 

#### Service-to-Router communication
This responsibility lies on `TradingService` implementation. It is a service, injected  into `TradingWebSocketHandler`, in order to let it communicate with `router-service` by directly calling `handleTradingRequest(...)`.
Upon initialization it tries to establish TCP connection to the **`ROUTER_HOST`**:**`ROUTER_BROKER_PORT`**. 
When `TradingWebSocketHandler` calls `handleTradingRequest(...)`,  `TradingService`  adds it's router-assigned ID as `SenderCompID` tag of [FixRequest message](#FixRequest) and attempts to send it. If connection is not established at this point, or router is not reachable - `TradingService`  tries to re-connect and re-send message.
Then, `handleTradingRequest` publishes a send operation status string, that's forwarded to the client, who made this request.

#### Router-to-Service communication
When connection is established, `TradingService` makes it's [`FixMessageProcessor`](#FixMessageProcessor) to subscribe to the input data publisher, while `TradingService` itself subscribes to `FixMessageProcessor`'s output flux, that will emit individual messages one-by-one.
`TradingService` expects 3 types of `FixMessage`s on it's receiving end:
- [`FixStockStateReport`](#FixStockStateReport): used to update cached state. Updates are delivered to clients via publishing of `InputEvent<StocksStateMessage>`.
- [`FixIdAssignationStockState`](#FixIdAssignationStockState): same as [`FixStockStateReport`](#FixStockStateReport), but also contains ID assigned to `broker-service` instance by `router`. 
- [`FixResponse`](#FixResponse): 
Other messages are ignored.

#### How Connection of a New Client is Processed

- **WebSocket Initialization**: The `TradingWebSocketHandler` manages WebSocket connections for clients. Upon a new connection, it initializes the state and subscribes to updates from the `TradingServiceImpl`.
- **Welcome Message**: The service sends an initial stock state to the client in JSON format upon successful connection establishment.

#### How Client's Trading Request is Processed

1. **Incoming JSON Message**: Clients send JSON trading requests via WebSocket.
2. **Validation and Conversion**: The `TradingWebSocketHandler` parses and validates the JSON payload, converting it into a `FixRequest` object.
3. **Forwarding to Router**: The `FixRequest` is forwarded to the `router-service` via `TradingServiceImpl`, which transforms it into a FIX message string and sends it over TCP.

#### How Exchange's Trading Response is Processed

1. **TCP Response Reception**: The `router-service` sends responses from exchanges back to the `broker-service` via TCP.
2. **FIX Parsing**: The `TradingServiceImpl` receives the FIX message response, parses it, and converts it into a structured object (`FixResponse`).
3. **Client Notification**: The parsed response is relayed to the appropriate WebSocket client.



### How State Update is Handled

1. **State Broadcasts**: The `router-service` periodically sends updated stock states as FIX messages.
2. **Deserialization**: These messages are parsed by the `TradingServiceImpl` and converted into JSON.
3. **Client Update**: The updated state is broadcast to all connected WebSocket clients.



### How TradingWebSocketHandler and TradingServiceImpl Work Together

- **WebSocket Handling**: The `TradingWebSocketHandler` manages all WebSocket connections and is the entry point for client requests.
- **Delegation**: It delegates the core business logic, such as forwarding requests and handling responses, to the `TradingServiceImpl`.
- **Event Publishing**: The `TradingServiceImpl` publishes updates, which the `TradingWebSocketHandler` subscribes to and forwards to clients.



The repository is modular and well-structured, making it easy to extend or modify components. Let me know if you’d like a similar analysis for other modules or need additional guidance. You can explore the repository further [here](https://github.com/r-kupin/fix-me).

### Exchange-service

### Router-service

### Eureka-server

### Gateway-service

## FIX communication
### FixMessageProcessor
### FixStockStateReport

### FixStateUpdateRequest

### FixRequest

### FixIdAssignationStockState

### FixResponse