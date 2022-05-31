# Gempukku LotR (Lord of the Rings)
This code originated in the following Github repository:
https://github.com/marcinsc

It has been transferred to Player's Committee for an ongoing support.

---

# Development

## Known Issues
  - Upgrading past Java 8 breaks some unit tests

## Requirements
  - Java 8

## Build
`mvn verify`

## Run
Access the application at: [http://localhost:17001/gemp-lotr/](http://localhost:17001/gemp-lotr/)

  - As Docker Container: `docker-compose up -d`
  - As Java Application: `java -jar web.jar` (in the `gemp-lotr-server/target` folder)
  - From IDE (VSCode): The application entrypoint is the `gemp-lotr-server` project
