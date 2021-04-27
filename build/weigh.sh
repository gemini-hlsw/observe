#!/bin/bash
cd $BUILDKITE_BUILD_CHECKOUT_PATH/modules/observe/web/client/target/scala-2.12/scalajs-bundler/main/
python $BUILDKITE_BUILD_CHECKOUT_PATH/build/weigh_in.py observe.js
python $BUILDKITE_BUILD_CHECKOUT_PATH/build/weigh_in.py observe.css
