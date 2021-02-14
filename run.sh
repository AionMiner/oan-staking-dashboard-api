#!/bin/bash

export redis_uri=redis://<ip>:<port>
export batch_node=true
export web3rpc_url=http://<ip>:8545
export amity_web3rpc_url=http://<ip>:<port>
export rate_api_url=https://min-api.cryptocompare.com/data/price?fsym=AION&tsyms=USD&api_key=<api_key>


java -jar target/oan-staking-dashboard-api-0.36.jar
