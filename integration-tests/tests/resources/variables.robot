*** Variables ***
${BASE_URL}             http://localhost:8081
${WIREMOCK_URL}         http://localhost:9090
${CONTENT_TYPE}         application/json

# Test RFID card data
${CARD_UID_1}           UID-TEST-ALPHA-001
${CARD_UID_2}           UID-TEST-BETA-002
${CARD_UID_UNKNOWN}     UID-DOES-NOT-EXIST-999
${CARD_OWNER}           Jean Dupont
${CARD_PHONE}           237650000001
${INITIAL_BALANCE}      5000
${DEBIT_AMOUNT}         1500
${LARGE_AMOUNT}         999999

# Machine / cycle data
${MACHINE_ID}           washer_01
${PULSE_COUNT}          1
${CYCLE_DURATION}       30

# Provider data
${PROVIDER_CAMPAY}      CAMPAY
${PROVIDER_MTN}         MTN
${PROVIDER_ORANGE}      ORANGE_MONEY
${CUSTOMER_PHONE_MTN}   237670123456
${CUSTOMER_PHONE_ORANGE}    237690123456
${CUSTOMER_PHONE_CAMPAY}    237650123456
