# EcoHealth JVM API

A [Scalatra](http://www.scalatra.org/2.3/guides/swagger.html) API server to return NLP annotations.

## How to run the server

```sh
$ git clone https://github.com/ecohealthalliance/jvm-nlp.git
$ cd jvm-nlp
$ chmod +x sbt
$ ./sbt
> container:start
```

## Making a request

```http -f POST http://localhost:8080/annotate/getNLPAnnotations content='it was 11am'```

## Why not use something else?

There is this: https://github.com/dasmith/stanford-corenlp-python

But, it’s not been updated in 2 years and is on an old stanford nlp version

There is also this: http://www.reidswanson.com/javadocs/stanfordtools/com/reidswanson/stanford/tools/serialize/CoreNlpSerializer.html
I don’t know how standard it is or how widely used. I still prefer the approach of only outputting the data we’re interested in.

## Why can't we use a swagger-generated client?

It doesn't handle Maps:

https://github.com/wordnik/swagger-spec/issues/38

So the api docs for this server are wrong.
