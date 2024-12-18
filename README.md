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
	- Human readable
    - Brokers send *Buy* and *Sell* orders in JSON format.
    - Responses include *Executed*, *Rejected*, and the up-to-date exchange states.
- **Spring**: Built using a spring platform, that allows convenient
  configuration of each service as well as possibilities for potential,
  modification & feature addition.
    - Services are built entirely around *Spring WebFlux* framework, ensuring asynchronous and efficient request processing with *ReactorNetty* web server.

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
The app is a multimodule maven project, allowing one-command compilation for all modules. It is containerized and can be deployed using Docker.

A `docker-compose.yml` file is included to simplify setup.

1. Compile: `mvn clean package`
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

Upon receiving, service responds with acknowledgement message, such as `Trading request sent` if request is correct and can be sent, or `Trading request not sent:` - followed by an explicit explanation why. If request was sent - client will receive a trading response message:
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
Provides clients with a single endpoint for sending trading requests and receiving responses.

#### Configuration
Used to specify key variables:
- **`ROUTER_HOST`** and **`ROUTER_BROKER_PORT`**: to access router
- **`SERVER_LISTEN_PORT`** and **`SERVER_LISTEN_ADDR`**: for clients to connect
- **`EUREKA_URI`**: eureka server's address

#### Client-to-Broker communication

##### Configuration
Happens via `websocket` protocol. It is configured via :
- **`SERVER_LISTEN_PORT`**  and **`SERVER_LISTEN_ADDR`** environment variables that specify host and port for `ReactorNetty` web server
- Handler path, set in the `WebSocketConfig`. As long service intends the use of a single `WebSocketHandler` - there is a single hard-coded path to it: `/ws/requests`.

##### Implementation
`TradingWebSocketHandler` is the only `WebSocketHandler` implemented, and it is responsible for direct communication with clients.

Upon initialization, it tries to establish connection to the `router-service`, on which it utterly depends. Then, regardless of the result, it starts asynchronously processing emissions from **client publishers**:
- **`onConnection`**: when new client connects, it should be welcomed with a [stock state message](#StockState). In order to compose it,  `TradingService` should be fetched for a current state, as it directly obtains it from the router via [FixStockStateReport](#FixStockStateReport) messages. If connection to the router is not established (initial attempt failed) - service tries to reconnect.
- **`onClientMessage`**: when client sends [trading request](#Trading), it gets parsed and transformed into [FixRequest](#FixRequest) which is handled to `TradingService` to be sent to the router.
	- As a part of wrapping client's request into FIX, `TradingWebSocketHandler` also set's `SenderSubId` tag to client's session id, to make it possible to perform response-to-sender matching.
	- Immediately after sending attempt, client receives a short message whether request was sent or not, if not - then, why? 
	- If connection to the router is not established - service tries to reconnect and only then sends the message.

#### Broker-to-Client communication
In order for service to be able to asynchronously reply to client's input - e.g. deliver **stock updates** and **trading responses** - Spring's `ApplicationEvent`s are employed in conjunction with [Project Reactor's `FluxSink`](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/FluxSink.html) and java's own `Executor`.

##### Events
In order to avoid usage of vanilla `ApplicationEvent`, the custom `InputEvent<E>` is created, where `E` is supposed to be a router-sent message, that should be delivered to the client. 

Events are published by `TradingService` using spring's `ApplicationEventPublisher`

###### `InputEvent<StocksStateMessage>`
The [contents](#StockState) of the event of this type are simply broadcasted to all connected clients.

###### `InputEvent<FixResponse>`
This type of event is only sent to client which is specified in [`FixResponse`](#FixResponse)'s `TargetSubID` tag. The value of the tag is drawn from [FixRequest](#FixRequest)'s `SenderSubId` tag by the service that generated response. This way, each response is forward to the same client, on whose request it answers.

##### Listeners
In order to embed events processing into the main reactive flow, `InputEventPublisher<E extends InputEvent>` is implemented. One instance of `InputEventPublisher` for each type of events are declared as a `@Bean` in the `EventConfig` class.

###### InputEventPublisher
While being an `ApplicationListener` it is also a `Consumer<FluxSink>`, which means it can handle events with `onApplicationEvent` by adding them to it's `BlockingQueue`, that in turn is drained by the `Executor`, that in the infinite cycle tries to take all elements from the `BlockingQueue` and put them into `FluxSink`.

When `BlockingQueue` gets empty, `Executor` gets blocked, until in the main thread new events are added via `onApplicationEvent`.

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

#### Broker-to-Router communication
This responsibility lies on `TradingService` implementation. It is a service, injected  into `TradingWebSocketHandler`, in order to let it communicate with `router-service` by directly calling `handleTradingRequest(...)`.
Upon initialization, it tries to establish TCP connection to the **`ROUTER_HOST`**:**`ROUTER_BROKER_PORT`**. 
When `TradingWebSocketHandler` calls `handleTradingRequest(...)`,  `TradingService`  adds it's router-assigned ID as `SenderCompID` tag of [FixRequest message](#FixRequest) and attempts to send it. If connection is not established at this point, or router is not reachable - `TradingService`  tries to re-connect and re-send message.
Then, `handleTradingRequest` publishes a send operation status string, that's forwarded to the client, who made this request.

#### Router-to-Broker communication
When connection is established, `TradingService` makes it's [`FixMessageProcessor`](#FixMessageProcessor) to subscribe to the input data publisher, while `TradingService` itself subscribes to `FixMessageProcessor`'s output flux, that will emit individual messages one-by-one.
`TradingService` expects 3 types of `FixMessage`s on it's receiving end:
- [`FixStockStateReport`](#FixStockStateReport): used to update cached state. Updates are delivered to clients via publishing of `InputEvent<StocksStateMessage>`.
- [`FixIdAssignationStockState`](#FixIdAssignationStockState): same as [`FixStockStateReport`](#FixStockStateReport), but also contains ID assigned to `broker-service` instance by `router`. 
- [`FixResponse`](#FixResponse): if generated by the `exchange-service` it is simply forwarded to the client via `InputEvent<FixResponse>` publication. It also can be generated by `router`, if requested `exchange-service` is not available. Then, it is also used to update cache - to remove stock which is no longer available. Updated cache gets published through dedicated event.

Other messages are ignored.

### Router-service
Provides connectivity between `broker`s and `exchange`s and maintains a reference state cache, which aggregates current states of all `exchange`s connected to it.

#### Configuration
Is fairly straightforward:
- **`ROUTER_ID`**:  according to the subject - router should assign IDs to all other services. This variable assigns ID to the router instance itself.
- **`ROUTER_HOST`**: host, on which router serves all services present in the infrastructure
- **`ROUTER_BROKER_PORT`** and **`ROUTER_EXCHANGE_PORT`**: ports for serving connections for respective services.
- **`SERVER_LISTEN_PORT`** and **`SERVER_LISTEN_ADDR`**: are not used, as router relies on 2 [`TcpServer`](#https://projectreactor.io/docs/netty/snapshot/reference/tcp-server.html)s that run in parallel to main `ReactorNetty` to serve for broker and exchange services.
- **`EUREKA_URI`**: eureka server's address

#### Structure
There is a clear distinction between connection management and processing of the requests, thus all logic concerning connection management is moved to dedicated `CommunicationKit` class.
Logic concerned with routing and cache maintenance, as well as all callback definitions are implemented in the `RouterService` itself.

##### `CommunicationKit`
Keeps maps `service_id:connection` and `service_id:fixProcessor` for each type of service. When new service connects:
1. It gets assigned a unique ID
2. New dedicated [`FixMessageProcessor`](#FixMessageProcessor) gets created to buffer service's input. In order to process that input:
	1. On-message `handlerCallback` is added
	2. On-error `errorCallback` is added
3. Welcoming message is sent to the service:
	- [`FixIdAssignation`](#FixIdAssignation) for new exchange
	- [`FixIdAssignationStockState`](#FixIdAssignationStockState) for new broker
4. Service's ID is set as an attribute to the connection, in order to be able to distinguish the correct `FixMessageProcessor`, to which service input should be redirected.
5. `Connection` and `FixMessageProcessor` are saved to the maps matched by service ID.

##### `OnConnectionHandler` classes
Are kind of redundant but added for clarity.

The standard approach to define a `TcpServer` is something like this:

```java
TcpServer.create().host(...).port(...)
    .doOnConnection(connection -> {/* send welcome message */})
    .handle((inbound, outbound) -> outbound.sendString(
                inbound.receive()
                    .asString(StandardCharsets.UTF_8)
                    .flatMap(string -> {/* process input, return output publisher */})
            ).then();
    ).bindNow();
```

This way obtaining `Connection`'s attribute isn't really possible, so `.doOnConnection(new OnConnectionHandler(communicationKit))` is used to replace `TcpServer`'s [handle](https://projectreactor.io/docs/netty/release/api/reactor/netty/tcp/TcpServer.html#handle-java.util.function.BiFunction-) method - does the same thing but allows direct access to the `Connection`.

#### Broker-to-Router communication

##### Connection
Each new `broker` connected is welcomed  with [`FixIdAssignationStockState`](#FixIdAssignationStockState). If `broker` disconnects, and then re-connects back, it is considered as a new `broker` instance and new ID is assigned to it.

##### [`FixRequest`](#FixRequest) Message
Gets forwarded to the exchange specified as `TargetCompID`. If this exchange is not available or present in the routing map - `router` itself generates rejection [`FixResponse`](#FixResponse) with `FixResponse.EXCHANGE_IS_NOT_AVAILABLE` as rejection reason.

##### [`FixStateUpdateRequest`](#FixStateUpdateRequest) Message
Generates [`FixStockStateReport`](#FixStockStateReport) message and forwards it directly to the `broker`  who sent request.

#### Router-to-Exchange communication

##### Connection
Each new `exchange` connected is welcomed  with [`FixIdAssignation`](#FixIdAssignation). If `exchange` disconnects, and then re-connects back, it is considered as a new `exchange` instance and new ID is assigned to it.

Each new `exchange` connected is expected to be a unique gateway to interact with a DB. Multiple `exchange`s connected to the same DB would create ambiguity and unpredictable behavior.

##### [`FixRequest`](#FixRequest) Message
Received from client -> forwarded to the target `exchange`.

#### Exchange-to-Router-to-Broker communication

##### [`FixStockStateReport`](#FixStockStateReport) Message
Expected to contain JSON `Map<String, Integer>` representation of the particular stock state. Upon receiving, corresponding cache entry is updated and broadcast-ed to all `broker`s.

##### [`FixResponse`](#FixResponse) Message
Is forwarded to the `broker`, on whose request it answers. 

### Exchange-service

#### Configuration
Is fairly straightforward:
- **`MAX_AMOUNT`**:  maximum amount of each instrument this exchange would accept. E.g. if the selling request arrives, and the amount after request fulfillment will exceed `MAX_AMOUNT` - such request will be rejected. Used for type safety, as app is a demonstrative one and only operates with integers.
- **`ROUTER_HOST`** and **`ROUTER_EXCHANGE_PORT`**: to access `router`.
- **`SERVER_LISTEN_PORT`** and **`SERVER_LISTEN_ADDR`**: are not used, no need to touch.
- Database credentials:
	- `DB_HOST`
	- `DB_PORT` 
	- `DB_NAME`  
	- `DB_USERNAME` 
	- `DB_PASSWORD`
- **`EUREKA_URI`**: eureka server's address

#### Structure

##### Repository
Uses reactive driver **`r2dbc`** instead of `jdbc`. This means - (indirectly) use `R2dbcEntityTemplate` instead of `JdbcTemplate`.
```java
import org.springframework.data.r2dbc.repository.Query;  
import org.springframework.data.repository.reactive.ReactiveCrudRepository;  
  
public interface StockRepo extends ReactiveCrudRepository<InstrumentEntry, Long> {  
    @Query("select * from stock where name=:nm")  
    Mono<InstrumentEntry> findByName(@Param("nm") String name);  
}
```

The actual repository instance is generated by `Spring` and injected into the service

##### Model
```java
@Table("stock")
public record InstrumentEntry(@Id Long id, String name, Integer amount) {}
```

##### Service
- Handles responses by performing CRUD on repository instance. After initialization, it checks availability and contents of database (negative values, etc.).
- If router is unavailable - tries to reconnect 5 times, then exits if no success.
- Is [`@Transactional`](#Transactions)

#### Communication
Router input handling mechanism is exactly the same as for `broker`.

On [`FixRequest`](#FixRequest) message - tries to perform operation, based on the result - generates response. If request was fulfilled, posts [`FixStockStateReport`](#FixStockStateReport) alongside with it, to indicate state change.

### Eureka-server
The **Eureka-server** is the discovery service for the entire microservices architecture. Its primary purpose is to manage service registration and discovery, enabling dynamic load balancing and fault tolerance.

#### Configuration
- **`EUREKA_HOST`** and **`EUREKA_PORT`**: address of eureka server
- **`eureka.client.register-with-eureka`**:
    - Set to `false` because this service acts as a standalone Eureka server and does not need to register itself.
- **`eureka.client.fetch-registry`**:
    - Set to `false` to prevent the server from attempting to fetch the registry from other Eureka instances. It is the source of truth for the registry.
- **`eureka.instance.leaseRenewalIntervalInSeconds`**:
    - Controls the frequency with which services renew their leases. Defaults to `5` seconds to ensure quicker detection of unavailable services.

#### Purpose
- Acts as the central registry for microservices.
- Stores information about all registered services (e.g., `broker-service`, `router-service`, `exchange-service`, `gateway-service`).
- Provides APIs for service discovery:
    - HTTP `/eureka/apps/` endpoints to fetch details of registered services.

#### Key Responsibilities
1. **Service Registration**: each service registers itself with Eureka on startup.
2. **Service Discovery**: services query the Eureka server to find other services dynamically.

#### Deployment Notes
- Accessible via `http://localhost:8761` by default.
- Includes a web dashboard to monitor registered services, view health statuses, and deregister faulty instances manually.

### Gateway-service
The **Gateway-service** acts as the entry point for external clients to the microservices infrastructure. It enables routing, load balancing, and WebSocket proxying.
#### Configuration
- **`GATEWAY_PORT`**: port which is actually accessed by the client
- **`spring.main.web-application-type`**: Configured as `reactive` to enable non-blocking, asynchronous request processing with Spring WebFlux.
- **`spring.cloud.gateway.routes`**:
    - Routes all requests matching the `/ws/**` path to the `broker-service` using the **`lb://broker-service`** URI, ensuring horizontal scalability of broker instances.
    - The **`StripPrefix`** filter ensures the `/ws/` prefix is removed before forwarding the request.
    - Supports WebSocket proxying with metadata configuration for seamless communication.

#### Purpose
- Acts as a single entry point for clients to interact with the microservices architecture.
- Provides load balancing and routing capabilities using Spring Cloud Gateway.
- Proxies WebSocket connections to the broker-service, enabling real-time bidirectional communication.

#### Key Responsibilities
1. **Route Requests**:
    - Uses Eureka to dynamically resolve the `broker-service` instances.
    - Forwards `/ws/**` requests to the appropriate broker instance using load balancing.
2. **WebSocket Proxying**: ensures clients can establish WebSocket connections to brokers seamlessly via `/ws/**`.
3. **Service Discovery**: registers itself with Eureka to be discoverable by other services in the architecture.

## FIX communication
All inter-service communication is done by using FIX-like protocol. Each FIX message is a chain of [tag](https://www.onixs.biz/fix-dictionary/5.0/fields_by_tag.html) = value pairs separated by `\u0001` delimiter. 

### Common tags
- **8**: `BeginString` - identifies beginning of new message and protocol version.
	- ALWAYS FIRST FIELD IN MESSAGE
	- Value: **string**
- **9**: [`BodyLength`](https://en.wikipedia.org/wiki/Financial_Information_eXchange#Body_length) - message length, in bytes, forward to the `CheckSum` field.
	- ALWAYS SECOND FIELD IN MESSAGE
	- Value: **int**
- **35**: `MsgType` - Defines message type.
	- ALWAYS THIRD FIELD IN MESSAGE
	- Values:
		- **`U1`**: 1st custom message type - [ID assignation message](#FixIdAssignation)
		- **`U2`**: 2nd custom message type - [Stocks state report](#FixStockStateReport)
		- **`U3`**: 3rd custom message type - [ID assignation message with stock states](#FixIdAssignationStockState)
		- **`U4`**: 4th custom message type - [Stock state update request](#FixStateUpdateRequest)
		- **`D`**: [`NewOrderSingle`](https://www.onixs.biz/fix-dictionary/5.0/msgType_D_68.html) - [New trading request](#FixRequest)
		- **`8`**: [`ExecutionReport`](https://www.onixs.biz/fix-dictionary/5.0/msgType_8_8.html) - [Trading response](#FixResponse)
- **49**: `SenderCompID` - `router`-assigned id of sender service
- **56**: `TargetCompID` - `router`-assigned id of destination service
- **10**: `CheckSum` - Three byte, simple checksum. I.e. serves, with the trailing `\u0001`, as the end-of-message delimiter.
	- ALWAYS LAST FIELD IN MESSAGE
	- Value: Always defined as **three digit characters**

### Main rules
1. Each message must contain mandatory tags: **8**, **9**, **35** and **10** in this order. Order of other tags is irrelevant.
2. Each message can be serialized to string and deserialized back from it.
	- `CheckSum` and `BodyLength` are calculated on serialization
	- `CheckSum` is verified on deserialization (when message is received on TCP inbound)
3. Each message must respect it's `MsgType` - verified on deserialization

### Messages

#### Trading 

##### Common tags
- **38**: `OrderQty` - amount of instrument items being traded
	- Value: **int**
- **54**: `Side` - side of order, e.g. action to be performed
	- Values: 
		- **1**: Buy
		- **2**: Sell
- **55**: `Symbol` - name of the [instrument](https://www.onixs.biz/fix-dictionary/5.0/compBlock_Instrument.html)
	- Value: **string**

##### FixRequest
`8=FIX.5.0|9=49|35=D|49=B00000|50=1|55=TEST1|54=1|38=1|56=E00000|10=068|`
Generated by `broker` based on client's [trading request](#Trading).
Specific tags:
- **50**: `SenderSubId` - id of the client who sent request. Needed to let `broker` decide to whom forward response when it arrives.

Broker also adds `SenderCompID` with his own ID to help the `router`.

##### FixResponse
`8=FIX.5.0|9=54|35=8|49=E00000|56=B00000|57=0|55=TEST1|54=1|38=1|39=2|10=026|`
`8=FIX.5.0|9=60|35=8|49=E00000|56=B00000|57=1|55=TEST1|54=1|38=1|39=8|103=4|10=039|`
Generated by `exchange` or `router` based on request and current stock state.
Specific tags:
- **57**: `TargetSubID` - copied from request's `SenderSubId`, as `request`'s sender is `response`'s target and vice versa.
- **39**: `OrdStatus` - result of request processing.
	- Values:
		- **0**: new (not used)
		- **2**: filled (success)
		- **8**: rejected (fail)
- **103**: `OrdRejReason` - rejection reason description. Tag is only present if order was rejected.
	- Values:
		- **0**: Not specified
		- **1**: Unsupported format
		- **2**: Target exchange is unavailable
		- **3**: Target exchange doesn't operate with requested instrument
		- **4**: Target exchange doesn't possess requested quantity
		- **5**: Action (side) should be either 1 or 2
		- **6**: Target exchange limits its amount of the instrument being sold.

#### Other

##### FixStockStateReport
`8=FIX.5.0|9=82|35=U2|49=R0000|58={"E00000":{"TEST2":2,"TEST1":1},"E00001":{"TEST3":3,"TEST4":4}}|10=244|`
Specific tags:
- **58**: `Text` - free format text string. In this case: serialized to JSON current stock state.

##### FixIdAssignationStockState
`8=FIX.5.0|9=93|35=U3|49=R00000|56=B00000|58={"E00000":{"TEST2":2,"TEST1":1},"E00001":{"TEST3":3,"TEST4":4}}|10=000|`
Basically a `FixStockStateReport`, but has a different `MsgType`. This type of message is only sent from `router` to newly connected `broker` as a welcome message, and it triggers `broker` to set its ID to the value of `TargetCompID`.

##### FixIdAssignation
`8=FIX.5.0|9=26|35=U1|49=R00000|56=E00000|10=247|`
Basically a `FixStockStateReport`, but has a different `MsgType` and without stock state - sent from `router` to newly connected `exchange` as a welcome message, and it triggers `exchange` to set its ID to the value of `TargetCompID`.

##### FixStateUpdateRequest
`8=FIX.5.0|9=26|35=U4|49=B00000|56=R00000|10=247|`
Basically a `FixIdAssignation`, but has a different `MsgType`. This type of message is sent from `broker` to `router` to explicitly ask for a state update.

### FixMessageProcessor
Due to the nature of TCP, each time input is reported, there is no guarantee that each input contains exactly one complete message. Therefore, some bufferization is needed and that's what `FixMessageProcessor` does.

It is employed by hooking it's `processInput` to the `Connection`'s inbound as a callback. This way, all traffic will be processed, buffered and separated.

Each individual message, as soon as it is complete, is emitted to the `sink`, which is obtainable `asFlux` in the service that uses it. Then, method hooked as a callback to that `Flux` can expect to have a single complete FIX message provided as an argument on each call.

In order to avoid confusion and messed-up input - one `FixMessageProcessor` is designed to handle a single `Connection`.

## Notes
### Transactions
In Spring applications, imperative and reactive transaction management is enabled by a `PlatformTransactionManager` bean that manages transactions for transactional resources, and resources are marked as transactional by annotating them with Spring’s `@Transactional` annotation.

At a lower level although, things are a little bit different. Transaction management needs to associate its transactional state with an execution.

- In imperative programming transactional state is bound to a `Thread` object, in which the Spring container started to execute the code.
- In a reactive application, this does not apply, because reactive execution requires multiple threads. The solution was to introduce a reactive alternative to the `ThreadLocal` storage class and this is Reactor’s `reactor.util.context.Context` interface. Contexts allow binding contextual data to a particular execution, and for reactive programming, this is a **`Subscription`** object.
    - The Reactor’s `Context` object lets Spring bind the transaction state, along with all resources and synchronizations, to a particular `Subscription` object.

Spring supports reactive transaction management through the **`ReactiveTransactionManager`** interface. It is a foundation for reactive transactional methods that return `Publisher<T>` types and for programmatic transaction management that uses **`TransactionalOperator`**.

Spring Data R2DBC provides the `R2dbcTransactionManager` class in the `org.springframework.r2dbc.connection` package that implements `ReactiveTransactionManager`. The `R2dbcTransactionManager` wraps around a reactive connection to the database to perform its job, and this connection is provided by the R2DBC driver.

```java
@EnableTransactionManagement
@SpringBootApplication
public class ReactiveApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactiveApplication.class, args);
    }

    @Bean
    ReactiveTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }
}
```

- The `@EnableTransactionManagement` annotation is needed to enable Spring’s annotation-driven transaction management capability, meaning support for `@Transactional` annotations.
- The `ConnectionFactory` bean is not declared explicitly. Spring Boot creates this bean from the configuration in the `application.yaml` (or the equivalent `application.properties`) and injects it wherever needed, in this case in our reactive transaction management bean.