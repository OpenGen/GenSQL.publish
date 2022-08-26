# inferenceql.publish

## Usage

``` shell
java -jar inferenceql.publish.jar \
  --db /absolute/path/to/db.edn \
  --schema /absolute/path/to/schema.edn \
  --path /absolute/path/to/directory
```

## Developing

In development you can consider including `-A:dev` when launching the REPL. This will add utility functions to `user` namespace that facilitate [reloading](https://clojure.org/guides/repl/enhancing_your_repl_workflow) code automatically after changes.

### Releasing

``` shell
clojure -T:build uberjar
```
