import asyncio
import json
import random
import string

import websockets

# Configuration
HOST = "localhost"  # Replace with your host
PORT = 8084  # Replace with your port


# Helper function to create random trading requests
def create_trade_request(target, instrument, action, amount):
    return {
        "target": target,
        "instrument": instrument,
        "action": action,
        "amount": amount,
    }


# Helper function to generate random strings for invalid cases
def random_string(length=5):
    return ''.join(random.choices(string.ascii_uppercase + string.digits, k=length))


# 8=FIX.5.0|35=D|49=B00001|50=1|55=TEST2|54=1|38=1386809|56=E00000|10=166|
# Asynchronous client simulator
async def sync_trade_simulator(uri_):
    async with websockets.connect(uri_) as websocket:
        # Receive the initial market state
        initial_message = await websocket.recv()
        print("Received initial market state:")
        print(initial_message)
        market_state = json.loads(initial_message)

        total_requests = 0

        while total_requests < 10000:
            # Extract valid exchanges and instruments
            exchanges = list(market_state["stocks"].keys())
            instruments = [
                (exchange, instrument, market_state["stocks"][exchange][instrument])
                for exchange, instruments in market_state["stocks"].items()
                for instrument in instruments.keys()
            ]

            # Determine if the request should be valid or invalid
            if total_requests % 3 == 0:  # Every third request is invalid
                if random.random() < 0.33:
                    exchange = random_string()  # Nonexistent exchange
                    instrument = random_string()  # Nonexistent instrument
                    amount = random.randint(1, 100)
                elif random.random() < 0.66 and instruments:
                    exchange, instrument, _ = random.choice(instruments)
                    amount = -random.randint(1, 100)  # Negative amount
                else:
                    exchange, instrument, available_quantity = random.choice(instruments)
                    amount = available_quantity + random.randint(1, 100)  # Exceeds available
                action = random.choice(["buy", "sell"])

            else:  # Valid request
                if not instruments:
                    print("No instruments available to trade.")
                    break
                exchange, instrument, available_quantity = random.choice(instruments)
                action = random.choice(["buy", "sell"])
                amount = random.randint(1, 100)


            # Create and send the request
            request = create_trade_request(exchange, instrument, action, amount)
            print(f"Request # {total_requests} ====================")
            print(f"Sending request: {request}")
            await websocket.send(json.dumps(request))

            # Wait for acknowledgement or rejection
            while True:
                response = await websocket.recv()
                print("Received response:")
                print(response)
                if response.startswith("Trading request not sent:"):
                    print("Request was rejected.")
                    break
                elif response == "Trading request sent":
                    print("Request accepted, waiting for trading response...")
                    continue

                # Handle trading response or updated stock state
                try:
                    parsed_response = json.loads(response)
                    if "ordStatus" in parsed_response:
                        print(f"Trading response received: {parsed_response}")
                        break
                    elif "stocks" in parsed_response:
                        market_state = parsed_response
                        print("Updated market state received.")
                        continue
                except json.JSONDecodeError:
                    print("Unrecognized message format.")
                    continue

            total_requests += 1


# Run the client
if __name__ == "__main__":
    uri = f"ws://{HOST}:{PORT}/ws/requests"
    asyncio.run(sync_trade_simulator(uri))
