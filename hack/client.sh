#!/bin/bash


while IFS=, read -r domain type ttl target comment action; do

    TOKEN=$(cli-auth -client_id sip-apps-dns-api-cli -scope "profile roles" -ports 58632 | jq .access_token | sed 's/"//g')
TOKEN=

    if [[ $type == "A" ]] ; then
        if [[ $target == "35.204.112.176" ]] ; then
            echo grpcurl -d '{"hostname":"176.112.204.35.bc.googleusercontent.com.","domain":"'$domain'","options":{"ttl":3600}}' -H "Authorization: Bearer $TOKEN" dns.vseth.ethz.ch:443 Dns/CreateCNameRecord
        elif ! echo $target | egrep "^129\.132.*$" >/dev/null; then
            echo external IP $target
        else
            echo grpcurl -d '{"ip":"'$target'","domain":"'$domain'","options":{"ttl":3600}}' -H "Authorization: Bearer $TOKEN" dns.vseth.ethz.ch:443 Dns/CreateARecord
        fi
    elif [[ $type == "CNAME" ]] ; then
        echo grpcurl -d '{"hostname":"'$target'","domain":"'$domain'","options":{"ttl":3600}}' -H "Authorization: Bearer $TOKEN" dns.vseth.ethz.ch:443 Dns/CreateCNameRecord
    fi

done < input.csv

