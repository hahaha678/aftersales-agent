package com.example.aftersales.aftersales.domain.query;

import com.example.aftersales.aftersales.domain.AftersaleStatus;
import jakarta.validation.constraints.*;

public class AftersalePageQuery {

    @Min(1)
    private int page = 1;

    @Min(1)
    @Max(100)
    private int size = 20;

    private AftersaleStatus status;

    public int getPage() {
        return page;
    }

    public void setPage(int value) {
        page = value;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int value) {
        size = value;
    }

    public AftersaleStatus getStatus() {
        return status;
    }

    public void setStatus(AftersaleStatus value) {
        status = value;
    }
}
