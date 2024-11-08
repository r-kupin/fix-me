package com.rokupin.broker.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ClientIDMessage implements Serializable {
    private String sessionID;
    private String routersID;
}
