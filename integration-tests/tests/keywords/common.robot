*** Settings ***
Library     RequestsLibrary
Library     JSONLibrary
Resource    ../resources/variables.robot

*** Keywords ***
Create Session To Service
    [Documentation]    Opens an HTTP session to the PaymentManagementService
    Create Session    payment    ${BASE_URL}    verify=False

Delete Session To Service
    Delete All Sessions

Register RFID Card
    [Documentation]    Registers a new RFID card and returns the response body
    [Arguments]    ${card_uid}    ${owner_name}=${CARD_OWNER}    ${phone}=${CARD_PHONE}
    &{body}=    Create Dictionary
    ...    cardUid=${card_uid}
    ...    ownerName=${owner_name}
    ...    phoneNumber=${phone}
    ${resp}=    POST On Session    payment    /api/rfid/register    json=${body}    expected_status=201
    RETURN    ${resp.json()}

Get Card Balance
    [Documentation]    Returns balance response for a card UID
    [Arguments]    ${card_uid}    ${required_amount}=${NONE}
    IF    $required_amount is not None
        ${resp}=    GET On Session    payment    /api/rfid/balance/${card_uid}
        ...    params=requiredAmount=${required_amount}    expected_status=200
    ELSE
        ${resp}=    GET On Session    payment    /api/rfid/balance/${card_uid}    expected_status=200
    END
    RETURN    ${resp.json()}

Debit Card
    [Documentation]    Debits the specified amount from the card
    [Arguments]    ${card_uid}    ${amount}    ${machine_id}=${MACHINE_ID}
    ...            ${pulse_count}=${PULSE_COUNT}    ${cycle_duration}=${CYCLE_DURATION}
    &{body}=    Create Dictionary
    ...    cardUid=${card_uid}
    ...    amount=${amount}
    ...    machineId=${machine_id}
    ...    pulseCount=${pulse_count}
    ...    cycleDuration=${cycle_duration}
    ...    description=Integration test debit
    ${resp}=    POST On Session    payment    /api/rfid/debit    json=${body}    expected_status=200
    RETURN    ${resp.json()}

Top Up Card With Cash
    [Documentation]    Top-ups a card via CASH channel
    [Arguments]    ${card_uid}    ${amount}
    &{body}=    Create Dictionary
    ...    cardUid=${card_uid}
    ...    amount=${amount}
    ...    channel=CASH
    ${resp}=    POST On Session    payment    /api/topup    json=${body}    expected_status=200
    RETURN    ${resp.json()}

Initiate Mobile Money Payment
    [Documentation]    Initiates a payment via the given provider
    [Arguments]    ${phone}    ${amount}    ${provider}    ${machine_id}=${MACHINE_ID}
    &{body}=    Create Dictionary
    ...    phoneNumber=${phone}
    ...    amount=${amount}
    ...    machineId=${machine_id}
    ...    pulseCount=${PULSE_COUNT}
    ...    cycleDuration=${CYCLE_DURATION}
    ...    provider=${provider}
    ...    description=Integration test payment
    ${resp}=    POST On Session    payment    /api/payments/initiate    json=${body}    expected_status=200
    RETURN    ${resp.json()}

Post Webhook
    [Documentation]    Posts a payment provider webhook payload
    [Arguments]    ${provider_path}    ${payload}
    ${resp}=    POST On Session    payment    /api/webhook/${provider_path}    json=${payload}    expected_status=200
    RETURN    ${resp.json()}
