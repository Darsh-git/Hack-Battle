# Person 2: BLE + Networking Development Brief

## Project
We are building a smartphone-based resilient emergency reporting system for disaster management.

The core purpose is:

> Allow a person who needs help to communicate an emergency report to nearby people even when internet connectivity is unavailable.

The system is hybrid:

### When internet is available

```text
User creates emergency report
        ↓
Internet
        ↓
Backend
```

### When internet is unavailable

```text
User creates emergency report
        ↓
BLE broadcast
        ↓
Nearby phone
        ↓
BLE broadcast
        ↓
Another nearby phone
        ↓
...
        ↓
Phone eventually gets internet
        ↓
Backend
```

The BLE network is therefore the critical offline communication layer.

---

## Tech stack
For the Android application:

- Java
- Android Studio
- Android BLE APIs
- Android Location/GPS APIs
- SQLite for local storage
- Android notification APIs

Do not use Kotlin.
Do not use Wi‑Fi Direct.
Do not use Nearby Connections.
Do not use LoRa or external hardware.

The prototype must work using the BLE hardware already present in normal Android phones.

---

## Person 2's responsibility
Person 2 owns:

### BLE
- BLE advertising
- BLE scanning
- Detecting nearby phones running our app
- Receiving emergency packets
- Broadcasting emergency packets
- Handling BLE packet encoding/decoding

### Networking
- Emergency packet format
- Packet IDs
- Duplicate detection
- TTL/hop count
- Store-and-forward logic
- Packet caching
- Relay scheduling
- Preventing infinite forwarding
- Basic battery-conscious operation

Person 1 handles most of the Android UI and application logic.

Person 1 and Person 2 work together on integrating the BLE layer into the Android app.

---

## First goal
Do not start by building the entire emergency application.

The first technical milestone is:

> Phone A → BLE → Phone B

Then:

> Phone A → Phone B → Phone C

No backend yet.
No dashboard yet.
No fancy UI.

We need to prove that the BLE relay mechanism works between real Android phones.

---

## Test 1: Basic BLE discovery
Build a minimal Android test application.

Phone A:

```text
ADVERTISING
Device ID: A
```

Phone B:

```text
SCANNING...

Found:
Device A
```

The app should be able to detect another instance of our application nearby.

Do not rely on the phone's Bluetooth device name as the application identity.

Use our own BLE service UUID / identifier.

---

## Test 2: Send a test packet
Create a small test packet.

For example:

```text
Packet ID: A12345
Type: MEDICAL
Severity: HIGH
Latitude: ...
Longitude: ...
Timestamp: ...
TTL: 5
```

The first prototype does not need to contain every field.

Start with:

```text
Packet ID
Message Type
TTL
```

Phone A broadcasts it.

Phone B receives it and logs:

```text
Received packet:
A12345
MEDICAL
TTL = 5
```

---

## Test 3: Store the packet
When B receives a packet:

```text
Receive
   ↓
Check Packet ID
   ↓
Already seen?
   ├── YES → Ignore
   └── NO
        ↓
     Store locally
```

The local database should eventually contain something like:

```text
packet_id
type
severity
latitude
longitude
timestamp
ttl
received_at
```

SQLite can be used for this.

---

## Test 4: Relay
After B receives a packet that it has not seen before:

```text
A
│
│ BLE
▼
B
│
│ BLE
▼
C
```

B should rebroadcast the packet.

The packet's hop count/TTL should decrease.

Example:

```text
A sends:

TTL = 5

B receives:

TTL = 4

C receives:

TTL = 3
```

When TTL reaches zero, stop forwarding.

---

## Test 5: Duplicate prevention
This is extremely important.

Suppose:

```text
A → B
A → C
B → C
C → B
```

Without duplicate detection, packets could circulate indefinitely.

Every packet therefore needs a unique:

```text
packetId
```

When a phone receives a packet:

```text
packetId already stored?
```

If yes:

```text
IGNORE
```

If no:

```text
STORE
RELAY
```

This needs to work reliably.

---

## Packet design
Do not send JSON over BLE for the final protocol.

JSON is useful during development because it is easy to inspect, but BLE advertising space is extremely limited.

Eventually create a compact binary packet.

Something conceptually like:

```text
┌──────────────┬───────┐
│ Field        │ Bytes │
├──────────────┼───────┤
│ Packet ID    │ 4     │
│ Type         │ 1     │
│ Severity     │ 1     │
│ Latitude     │ 4     │
│ Longitude    │ 4     │
│ Timestamp    │ 4     │
│ TTL          │ 1     │
└──────────────┴───────┘
```

This is only an initial example.

Do not blindly implement this exact layout yet.

First investigate the actual Android BLE advertising payload constraints and design the final packet around those constraints.

---

## Important: BLE advertising vs connection
Our primary offline mechanism is BLE advertising/scanning.

The important property is:

> A phone can broadcast a small packet without establishing a Bluetooth connection with every receiving phone.

We do not want:

```text
A → "Connect?"
B → "Accept?"
```

for every relay.

The relay network should therefore operate through advertisement/scanning, not GATT connections.

A richer communication channel is not part of the MVP.

---

## Relay behavior
Every phone running the application should conceptually perform two roles:

```text
             ┌──────────────┐
             │   PHONE B    │
             └──────────────┘
                    ▲
                    │ BLE scan
                    │
              receives packets
                    │
                    ▼
              local database
                    │
                    ▼
              BLE advertiser
```

So a phone is simultaneously:

- Scanner
- temporary storage
- broadcaster

This creates the opportunistic relay network.

---

## Do not flood aggressively
Do not implement:

> Receive packet → immediately broadcast continuously forever.

That would create unnecessary BLE traffic and battery drain.

Eventually we need a relay scheduler.

For the first prototype, something simple is enough:

```text
Receive new packet
       ↓
Store packet
       ↓
Wait short randomized delay
       ↓
Broadcast packet
       ↓
Stop/reduce frequency
```

Later we can improve the forwarding strategy.

---

## Future relay priority
Eventually we may want packets to have priorities.

For example:

```text
CRITICAL
HIGH
MEDIUM
LOW
```

Critical reports should be rebroadcast more aggressively than low-priority reports.

But do not implement sophisticated routing yet.

First prove basic forwarding.

---

## Internet gateway
Person 2 also needs to expose the information required for Person 1 to integrate the gateway behavior.

Eventually:

```text
BLE packet received
        ↓
Stored locally
        ↓
Internet becomes available
        ↓
Android app uploads packet
        ↓
Backend
```

Person 2 does not need to build the backend.

Person 3 owns the backend.

Person 1 handles the Android application's connectivity/upload integration.

Person 2 needs to make sure the received packet is available to the rest of the Android application.

---

## What to build first
Copilot should help build these modules separately:

```text
BLEManager
    │
    ├── startAdvertising()
    ├── stopAdvertising()
    ├── startScanning()
    └── stopScanning()

PacketManager
    │
    ├── encodePacket()
    ├── decodePacket()
    ├── generatePacketId()
    └── validatePacket()

RelayManager
    │
    ├── receivePacket()
    ├── isDuplicate()
    ├── storePacket()
    └── scheduleRelay()

PacketRepository
    │
    ├── savePacket()
    ├── getPacket()
    ├── hasPacket()
    └── removeExpiredPackets()
```

The exact class structure can change, but keep BLE, packet logic, relay logic, and storage separated.

---

## Development order
Tell Copilot to work in this order:

### Phase 1: BLE advertising
Get one phone to advertise.

### Phase 2: BLE scanning
Get another phone to detect it.

### Phase 3: Packet transmission
Transmit a tiny test packet.

### Phase 4: Packet parsing
Decode the packet on the receiving phone.

### Phase 5: Local storage
Save received packets in SQLite.

### Phase 6: Duplicate detection
Ignore packets already received.

### Phase 7: Relay
Automatically rebroadcast new packets.

### Phase 8: Three-phone test
```text
A → B → C
```

### Phase 9: Five-phone test
```text
A → B → C → D → E
```

### Phase 10: Integrate with Person 1's actual emergency-report system.

---

## Success criteria for Person 2
Person 2 is done with the first major milestone when we can physically demonstrate:

```text
PHONE A
Creates test emergency
       ↓
BLE
       ↓
PHONE B
Receives + stores
       ↓
BLE
       ↓
PHONE C
Receives + stores
```

And:

- No internet required.
- No Wi‑Fi Direct.
- No pairing.
- No per-device connection approval.
- Duplicate packets do not endlessly circulate.
- TTL works.
- The packet survives while stored on a phone.

### Most important instruction to Copilot
Do not build the whole application. Build and test the BLE networking layer independently first.

If A → B → C does not work reliably, everything else is irrelevant. Once that works, Person 1 can plug the networking layer into the actual emergency-reporting UI and the rest of the team can build the backend and dashboard in parallel.

---

## Direct Copilot prompt
Use this as the final brief to paste into Copilot:

```text
# PERSON 2: BLE + NETWORKING DEVELOPMENT BRIEF

## Project
We are building a smartphone-based resilient emergency reporting system for disaster management.

The core purpose is:

> Allow a person who needs help to communicate an emergency report to nearby people even when internet connectivity is unavailable.

The system is hybrid:

### When internet is available

User creates emergency report
        ↓
Internet
        ↓
Backend

### When internet is unavailable

User creates emergency report
        ↓
BLE broadcast
        ↓
Nearby phone
        ↓
BLE broadcast
        ↓
Another nearby phone
        ↓
...
        ↓
Phone eventually gets internet
        ↓
Backend

The BLE network is therefore the critical offline communication layer.

## Tech stack
For the Android application:

- Java
- Android Studio
- Android BLE APIs
- Android Location/GPS APIs
- SQLite for local storage
- Android notification APIs

Do not use Kotlin.
Do not use Wi‑Fi Direct.
Do not use Nearby Connections.
Do not use LoRa or external hardware.

The prototype must work using the BLE hardware already present in normal Android phones.

## Person 2's responsibility
Person 2 owns:

### BLE
- BLE advertising
- BLE scanning
- Detecting nearby phones running our app
- Receiving emergency packets
- Broadcasting emergency packets
- Handling BLE packet encoding/decoding

### Networking
- Emergency packet format
- Packet IDs
- Duplicate detection
- TTL/hop count
- Store-and-forward logic
- Packet caching
- Relay scheduling
- Preventing infinite forwarding
- Basic battery-conscious operation

Person 1 handles most of the Android UI/application logic.
Person 1 and Person 2 work together on integrating the BLE layer into the Android app.

## First goal
Do not start by building the entire emergency application.

The first technical milestone is:

> Phone A → BLE → Phone B

Then:

> Phone A → Phone B → Phone C

No backend yet.
No dashboard yet.
No fancy UI.

We need to prove that the BLE relay mechanism works between real Android phones.

## Development order
Work in this order:

1. BLE advertising
2. BLE scanning
3. Packet transmission
4. Packet parsing
5. Local storage in SQLite
6. Duplicate detection
7. Relay with TTL decrement
8. Three-phone test
9. Five-phone test
10. Integrate with Person 1's app

## Important constraints
- Build and test the BLE networking layer independently first.
- Do not build the whole application.
- No internet required for the first milestone.
- No Wi‑Fi Direct.
- No pairing.
- No per-device connection approval.
- Use BLE advertisement/scanning, not GATT connections.
- Keep BLE, packet logic, relay logic, and storage separated.
- Use a unique packet ID and prevent endless relaying.
- Build the smallest working test app first, then evolve it.

## Implementation targets
Build these modules separately:

- BLEManager
  - startAdvertising()
  - stopAdvertising()
  - startScanning()
  - stopScanning()

- PacketManager
  - encodePacket()
  - decodePacket()
  - generatePacketId()
  - validatePacket()

- RelayManager
  - receivePacket()
  - isDuplicate()
  - storePacket()
  - scheduleRelay()

- PacketRepository
  - savePacket()
  - getPacket()
  - hasPacket()
  - removeExpiredPackets()

The exact class structure can change, but keep the concerns separated.

## Success criteria
Person 2 is done with the first major milestone when we can physically demonstrate:

PHONE A
Creates test emergency
       ↓
BLE
       ↓
PHONE B
Receives + stores
       ↓
BLE
       ↓
PHONE C
Receives + stores

Requirements:
- No internet required.
- No Wi‑Fi Direct.
- No pairing.
- Duplicate packets do not endlessly circulate.
- TTL works.
- The packet survives while stored on a phone.

If A → B → C does not work reliably, everything else is irrelevant.
```
