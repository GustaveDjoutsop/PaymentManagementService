*** Variables ***
${BASE_URL}             http://localhost:8081
${WIREMOCK_URL}         http://localhost:9090
${CONTENT_TYPE}         application/json

# ── Auth0 credentials (dev tenant) ───────────────────────────────────────────
# These are the DEV defaults. Override via --variable on staging/prod CI runs.
# Do NOT use these credentials on staging or production environments.
${AUTH0_TOKEN_URL}      https://dev-iuo6si32jobgnmod.eu.auth0.com/oauth/token
${AUTH0_CLIENT_ID}      qrhBuc3lsJfRqsP8xKWAO334DOsseidM
${AUTH0_CLIENT_SECRET}  5_L-EOe5BBn3g2V9egVHWnKJkabkxtqom4kIqxluEx8J0N6VEbFtG7XPdoCZln7G
${AUTH0_AUDIENCE}       https://smartlaundry.api
# All scopes needed by this service's integration tests
${AUTH0_SCOPE}          sls-rfid-read sls-rfid-manage sls-rfid-debit sls-payment-read sls-payment-initiate sls-topup-manage

# ── Test RFID card data ───────────────────────────────────────────────────────
${CARD_UID_1}           UID-TEST-ALPHA-001
${CARD_UID_2}           UID-TEST-BETA-002
${CARD_UID_UNKNOWN}     UID-DOES-NOT-EXIST-999
${CARD_OWNER}           Jean Dupont
${CARD_PHONE}           237650000001
${INITIAL_BALANCE}      5000
${DEBIT_AMOUNT}         1500
${LARGE_AMOUNT}         999999

# ── Machine / cycle data ─────────────────────────────────────────────────────
${MACHINE_ID}           washer_01
${PULSE_COUNT}          1
${CYCLE_DURATION}       30

# ── Provider data ─────────────────────────────────────────────────────────────
${PROVIDER_CAMPAY}      CAMPAY
${PROVIDER_MTN}         MTN
${PROVIDER_ORANGE}      ORANGE_MONEY
${CUSTOMER_PHONE_MTN}   237670123456
${CUSTOMER_PHONE_ORANGE}    237690123456
${CUSTOMER_PHONE_CAMPAY}    237650123456
