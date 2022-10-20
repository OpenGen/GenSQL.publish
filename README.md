# inferenceql.publish

## Usage

``` shell
java -jar inferenceql.publish.jar --help
```

## Developing

### Setup

``` shell
pnpm install
clojure -T:build build
```

### Running locally from the REPL

In development you can consider including `-A:dev` when launching the REPL. This will add utility functions to `user` namespace that facilitate [reloading](https://clojure.org/guides/repl/enhancing_your_repl_workflow) code automatically after changes.

``` shell
❯ clj -A:dev
Clojure 1.11.1
user=> (go)
Initializing system...done!
Starting system...done!
```

### Running locally from the command-line

``` shell
clojure -M:run --help
```

### Including SPPL support

[inferenceql.gpm.sppl](https://github.com/inferenceql/inferenceql.gpm.sppl) is not included as a dependency by default. If you intend to use SPPL models you will want to provide the `sppl` alias when either launching the REPL or running build commands. 

If you intend to query SPPL models you will need to have a Python environment with SPPL installed into it in such a way that [libpython-clj](https://github.com/clj-python/libpython-clj) can find it. The easiest way to use that is to start the inferenceql.gpm.sppl Nix development shell before running publish.

``` shell
nix develop github:inferenceql/inferenceql.gpm.sppl -c java -jar inferenceql.publish.jar --help
```

``` shell
nix develop github:inferenceql/inferenceql.gpm.sppl -c clj -A:dev:sppl
```

### Building a JAR file

Running the following command will produce a JAR file the `./target` directory.

``` shell
clojure -T:build:sppl uberjar
```
