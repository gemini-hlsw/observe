`brew install certbot` (or other preferred installation method)

```
sudo certbot certonly \
    --manual \
    --agree-tos \
    --preferred-challenges dns-01 \
    --rsa-key-size 4096 \
    -d observe-test.cl.lucuma.xyz
```

(or `-d observe-test.hi.lucuma.xyz`)
