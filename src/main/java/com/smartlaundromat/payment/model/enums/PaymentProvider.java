package com.smartlaundromat.payment.model.enums;

/**
 * Identifies which payment provider processed a transaction.
 * <ul>
 *   <li>CAMPAY       — CamPay mobile money gateway</li>
 *   <li>MTN          — MTN MoMo direct</li>
 *   <li>ORANGE_MONEY — Orange Money direct</li>
 *   <li>EQLINK       — EQLink built-in payment (QR code / EQLink app)</li>
 * </ul>
 */
public enum PaymentProvider {
    CAMPAY,
    MTN,
    ORANGE_MONEY,
    EQLINK
}
