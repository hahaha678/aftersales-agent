package com.example.aftersales.common.exception;

/** Temporary boundary: remove a throw only when the corresponding service is implemented. */
public class ContractNotImplementedException extends RuntimeException {

    public ContractNotImplementedException() {
        super("当前接口仅完成契约设计，业务尚未实现");
    }
}
