CREATE TABLE stock
(
    id     INT         NOT NULL AUTO_INCREMENT,
    name   VARCHAR(60) NOT NULL,
    amount INT         NOT NULL,
    UNIQUE (name),
    PRIMARY KEY (id)
);