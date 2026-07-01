package com.fateironist.jawf.model;

import lombok.Data;

@Data
public class User {
    private Long id;
    private String name;
    private String email;
    private Integer age;
}
