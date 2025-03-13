package com.rokupin.client.model.form;

import jakarta.validation.constraints.Size;

public record LoginForm(
        @Size(min = 2,max = 15,
                message = "Username should be between 2 and 15 characters")
        String username,
        @Size(min = 4,
                message = "Password should be at least 4 characters long")
        String password
) {}
