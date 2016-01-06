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

```
curl -X POST http://localhost:8080/annotate/getNLPAnnotations -H "Content-Type: application/json" -d '{"text":"it was 11am in Brooklyn", "tiers":{}}'
```

## Why not use something else?

There is this: https://github.com/dasmith/stanford-corenlp-python

But, it’s not been updated in 2 years and is on an old stanford nlp version

There is also this: http://www.reidswanson.com/javadocs/stanfordtools/com/reidswanson/stanford/tools/serialize/CoreNlpSerializer.html
I don’t know how standard it is or how widely used. I still prefer the approach of only outputting the data we’re interested in.

## Why can't we use a swagger-generated client?

It doesn't handle Maps:

https://github.com/wordnik/swagger-spec/issues/38

So the api docs for this server are wrong.

## License
Copyright 2016 EcoHealth Alliance

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
