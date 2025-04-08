# opia-server
## Api
### Post
- postId
- asEventId: posted in the name of that event
- API response: return list of attachments

### PostAttachment
- postId, mediaId

## Deployment
- requires `rsync` on server
- `rsync -a --exclude build --exclude uploads . root@opia.app:~/opia-server/`
- old: `rsync -a --exclude build --exclude uploads opia-server root@opia.app:~/`

## DB
- NOTE: partial index doesn't work in dev environment, therefore `.singleOrNull` might fail
  - using `.firstOrNull`

### reset db
- `docker container rm opia-server-db-1`
- `docker volume rm opia-server_pg-volume`

## Bash Client
### Prod
```shell
export host=https://staging.opia.app
export ws_host=wss://staging.opia.app/rt
```

### Dev
```shell
export host=http://localhost:8080
export ws_host=ws://localhost:8080/rt
```

## Installations Api
### List
```shell
curl "$host/api/v1/installations"
```

### Create
```shell
output=$(curl -s --json '{"id":"'$(uuidgen)'","name":"Curl client","desc":"README","os":"desktop","client_vname":"opia_readme/1"}' -X PUT "$host/api/v1/installations")
echo "$output"
export installation_id=$(jq -r '.data.id' <<< "$output")
echo "installation_id: $installation_id"
```

## Actor Properties Api
### Create
```shell
# 044 668 18 00 (https://github.com/google/libphonenumber)
export phone_no="+41446681800"
output=$(curl -s --json '{"content":"'$phone_no'"}' -H "Installation-Id: $installation_id" "$host/api/v1/actors/properties")
echo "$output"
export actor_phone_no_id=$(jq -r '.data.id' <<< "$output")
echo "actor_phone_no_id: $actor_phone_no_id"
export phone_verification_code=$(jq -r '.data.verification_code' <<< "$output")
echo "phone_verification_code: $phone_verification_code"
```

## Actors Api
### List (Authentication required)
```shell
curl -H "Authorization: Bearer $access_token" "$host/api/v1/actors"
```

### Query self (Authentication required)
```shell
curl -H "Authorization: Bearer $access_token" "$host/api/v1/actors/$actor_id"
```

### Create
```shell
output=$(curl -s --json '{"handle":"username","name":"User Name","secret":"secret12"}' -H "Challenge-Response: $actor_phone_no_id=$phone_verification_code" -H "Installation-Id: $installation_id" "$host/api/v1/actors")
echo "$output"
export actor_id=$(jq -r '.data.id' <<< "$output")
echo "actor_id: $actor_id"
```

### Patch (self)
```shell
output=$(curl -s -X PATCH --json '{"name":"New Name :)","desc":"New account bio :)"}' -H "Authorization: Bearer $access_token" "$host/api/v1/actors")
echo "$output"
```
- possible properties (leave out field not to be updated, or `null`): `name`, `desc`

## Auth Sessions Api
### List
```shell
curl -H "Authorization: Bearer $access_token" "$host/api/v1/auth_sessions"
```

### Login with handle
- requires `Installation`
- redo when receiving "invalid or expired token", though apps use refresh route
```shell
output=$(curl -s --json '{"unique":"username","secret":"secret12","cap_chat":true}' -H "Installation-Id: $installation_id" "$host/api/v1/auth_sessions")
echo "$output"
export actor_id=$(jq -r '.data.actor_id' <<< "$output")
echo "actor_id: $actor_id"
export sess_id=$(jq -r '.data.id' <<< "$output")
echo "sess_id: $sess_id"
export access_token=$(jq -r '.data.access_token' <<< "$output")
echo "access_token: $access_token"
export refresh_token=$(jq -r '.data.refresh_token' <<< "$output")
echo "refresh_token: $refresh_token"
```

### Login with phone-no
- requires `Installation`

### Refresh Token
```shell
output=$(curl -s -X POST -H )
```

## Medias Api
### List
```shell
curl "$host/api/v1/medias"
```

### Create (set Actor Profile)
```shell
output=$(curl -s -F "file=@src/main/resources/static/opia_logo_21.jpg" \
-F 'ref={"type":"actor","id":"'$actor_id'","property":"profile"}' \
-H "Authorization: Bearer $access_token" -H "Content-Type: multipart/form" "$host/api/v1/medias")
echo "$output"
# $media_id with apostrophe so it's "nullable" (-> create `Post`)
export media_id=$(jq '.data.id' <<< "$output")
echo "media_id: $media_id"
```

### Create (Attach to Post)
```shell
output=$(curl -s -F "file=@src/main/resources/static/opia_logo_21.jpg" \
-F 'ref={"type":"post","id":"'$post_id'"}' \
-H "Authorization: Bearer $access_token" -H "Content-Type: multipart/form" "$host/api/v1/medias")
echo "$output"
# $media_id with apostrophe so it's "nullable" (-> create `Post`)
export media_id=$(jq '.data.id' <<< "$output")
echo "media_id: $media_id"
```

## Events Api
### List
```shell
curl -H "Authorization: Bearer $access_token" "$host/api/v1/events"
```

### Create
```shell
output=$(curl -s --json '{"name":"Eventpilled","desc":"Are you eventpilled? Then join us for our great idk bla lorem ipsum"}' -H "Authorization: Bearer $access_token" "$host/api/v1/events")
echo "$output"
export event_id=$(jq -r '.data.id' <<< "$output")
echo "event_id: $event_id"
```

## Posts Api
### List
```shell
curl -H "Authorization: Bearer $access_token" "$host/api/v1/posts"
```

### Create
```shell
# $media_id with apostrophe so it's "nullable"
output=$(curl -s --json '{"title":"Post Title","text":"Text","event_id":"'"$event_id"'","media_ids":['"$media_id"']}' -H "Authorization: Bearer $access_token" "$host/api/v1/posts")
echo "$output"
export post_id=$(jq -r '.data.id' <<< "$output")
echo "post_id: $post_id"
```

## Feed Api
### List
```shell
curl -H "Authorization: Bearer $access_token" "$host/api/v1/feed"
```

## Messages Api
### List (Websocat client)
```shell
websocat -H="Authorization: Bearer $access_token2" $ws_host
```

### Post
```shell
ioid2='b06a8bc4-68dd-4f85-b780-2fdfb290dc26'
packet='{"rcpt_ioid":"'$ioid2'","dup":0,"seqno":0,"payload_enc":"'$(base64 <<< hi)'"}'
output=$(curl -s --json '{"id":"'$(uuidgen)'","rcpt_id":"'$actor2_id'","timestamp":"2023-07-11T10:16:34Z","packets":['$packet']}' -H "Authorization: Bearer $access_token" "$host/api/v1/messages")
echo "$output"
export message_id=$(jq -r '.data.id' <<< "$output")
echo "message_id: $message_id"
```

## Development Api
### Send Test message
- no account necessary  
```shell
curl -X POST $host/api/v1/send/test/username
```

## Test Account
### Login
- requires `Installation`
```shell
# link
output=$(curl -s --json '{"unique":"test","secret":"password","cap_chat":true}' -H "Installation-Id: $installation_id" "$host/api/v1/auth_sessions")
echo "$output"
export actor2_id=$(jq -r '.data.actor_id' <<< "$output")
echo "actor2_id: $actor2_id"
export ioid2=$(jq -r '.data.ioid' <<< "$output")
echo "ioid2: $ioid2"
export access_token2=$(jq -r '.data.access_token' <<< "$output")
echo "access_token2: $access_token2"
```

### Setup (Only Once)
```shell
export phone_no2="+41446681801"
output=$(curl -s --json '{"content":"'$phone_no2'"}' -H "Installation-Id: $installation_id" "$host/api/v1/actors/properties")
echo "$output"
export actor2_phone_no_id=$(jq -r '.data.id' <<< "$output")
echo "actor2_phone_no_id: $actor2_phone_no_id"
export phone_verification_code=$(jq -r '.data.verification_code' <<< "$output")
echo "phone_verification_code: $phone_verification_code"

output=$(curl -s --json '{"handle":"test","name":"Test User","secret":"password"}' -H "Challenge-Response: $actor2_phone_no_id=$phone_verification_code" -H "Installation-Id: $installation_id" "$host/api/v1/actors")
echo "$output"
export actor2_id=$(jq -r '.data.id' <<< "$output")
echo "actor2_id: $actor2_id"
```

## Notes
- `set -e`: also allows handling CTRL-C of individual commands
