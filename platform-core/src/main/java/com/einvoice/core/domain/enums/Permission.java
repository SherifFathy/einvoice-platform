package com.einvoice.core.domain.enums;

/** Fine-grained permission keys used by @RequiresPermission AOP checks. */
public enum Permission {
    CREATE_INVOICE,
    CREATE_CUSTOMER,
    CREATE_ITEM,
    EDIT_INVOICE,
    EDIT_CUSTOMER,
    EDIT_ITEM,
    DELETE_INVOICE,
    DELETE_CUSTOMER,
    DELETE_ITEM,
    TRANSFER_INVOICE,
    REFRESH_INVOICE,
    VIEW_INVOICE_LIST,
    VIEW_CUSTOMER_LIST,
    VIEW_ITEM_LIST
}
