FROM nixos/nix:latest AS builder

WORKDIR /var/build

COPY . /var/build

COPY ./resources/nix.conf /etc/nix/nix.conf

# # Build the uberjar
RUN nix-channel --update && nix develop . -c clojure -P -M:build:sppl
RUN nix develop . -c pnpm install
RUN nix develop . -c clojure -T:build:sppl uberjar

# Second stage!
FROM nixos/nix:latest

COPY ./resources/nix.conf /etc/nix/nix.conf
COPY --from=builder /var/build/target/inferenceql.publish.jar /var/iql/publish.jar

RUN nix-channel --update && nix develop github:inferenceql/inferenceql.gpm.sppl -c java -jar /var/iql/publish.jar --help

ENTRYPOINT ["nix", "develop", "github:inferenceql/inferenceql.gpm.sppl", "-c", "java", "-jar", "/var/iql/publish.jar"]
