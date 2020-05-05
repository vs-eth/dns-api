#!/bin/bash

set -x

while IFS=, read -r domain type ttl target comment action; do

    TOKEN=$(cli-auth -client_id sip-apps-dns-api-cli -scope "profile roles" -ports 58632 | jq .access_token | sed 's/"//g')

    if [[ $type == "A" ]] ; then
        if [[ $target == "35.204.112.176" ]] ; then
            grpcurl -d '{"hostname":"176.112.204.35.bc.googleusercontent.com.","domain":"'$domain'","options":{"externallyViewable":true,"ttl":3600}}' -H "Authorization: Bearer $TOKEN" dns.vseth.ethz.ch:443 Dns/CreateCNameRecord
        elif ! echo $target | egrep "^129\.132.*$" >/dev/null; then
            echo external IP $target
        else
            grpcurl -d '{"ip":"'$target'","domain":"'$domain'","options":{"ttl":3600}}' -H "Authorization: Bearer $TOKEN" dns.vseth.ethz.ch:443 Dns/CreateARecord
        fi
    elif [[ $type == "CNAME" ]] ; then
        grpcurl -d '{"hostname":"'$target'","domain":"'$domain'","options":{"ttl":3600}}' -H "Authorization: Bearer $TOKEN" dns.vseth.ethz.ch:443 Dns/CreateCNameRecord
    fi
    sleep 3

done < input.csv

