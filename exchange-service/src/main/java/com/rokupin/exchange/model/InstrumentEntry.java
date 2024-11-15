package com.rokupin.exchange.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;


@Table("stock") // Name of the table in your database
public record InstrumentEntry(@Id Long id, String name, Integer amount) {
}
