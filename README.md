# OSRSPS

A private Old School RuneScape test server for developing RuneLite plugins against 1:1 recreations of live content.
Our own engine in Kotlin on Java 21, using [RSProt](https://github.com/blurite/rsprot) for the network protocol.

Target revision: **241** (see `src/main/kotlin/osrsps/Revision.kt`).

## Never commit Jagex files

The game cache, the client, XTEA keys, RSA keys and gameplay recordings stay out of this repo.
They live under `data/` (git-ignored) on the machine that runs the server.

## Build and run

Needs JDK 21. The Gradle wrapper downloads Gradle itself.

Sandbox VM (bash):

```bash
./gradlew build run
```

Windows (PowerShell):

```powershell
.\gradlew.bat build run
```

A successful run logs `OSRSPS server skeleton, revision 241`.

## Get the cache

The server downloads the live revision 241 cache and its XTEA keys from the [OpenRS2 archive](https://archive.openrs2.org)
into `data/cache/` (set `OSRSPS_DATA` to use another folder). Run this on the sandbox VM:

```bash
./gradlew run --args=fetch-cache   # download, then print a summary
./gradlew run --args=cache-info    # summarise an existing download
```

`data/cache-source.txt` records which OpenRS2 cache was used.

## Run the server

```bash
./gradlew run --args=serve          # JS5 and game on port 43594
./gradlew run --args="serve 50000"  # another port
```

The first start generates a 1024-bit RSA key in `data/rsa.key` and writes its modulus to `data/rsa-modulus.txt`.
Any username and password log in, and you appear in Lumbridge.

## Connect RuneLite through RSProx

From Windows (PowerShell), forward the game port through the jump host and keep this window open:

```powershell
ssh -J root@192.168.0.210 -L 43594:127.0.0.1:43594 sam@10.99.0.10
```

Then add a target to `%USERPROFILE%\.rsprox\proxy-targets.yaml`, with the modulus from `data/rsa-modulus.txt` on the VM:

```yaml
config:
  - name: OSRSPS
    jav_config_url: https://client.blurite.io/jav_local_241.ws
    revision: 241.1
    modulus: <contents of data/rsa-modulus.txt>
```

`jav_local_241.ws` is Blurite's jav_config pointing the client at 127.0.0.1, which the tunnel carries to the VM.
