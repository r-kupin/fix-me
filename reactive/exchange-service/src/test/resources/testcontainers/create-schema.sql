CREATE TABLE stock
(
    id     INT         NOT NULL AUTO_INCREMENT,
    name   VARCHAR(60) NOT NULL,
    amount INT         NOT NULL,
    UNIQUE (name),
    PRIMARY KEY (id)
);

insert into stock (id, name, amount)
values (1, 'TEST1', 1);
insert into stock (id, name, amount)
values (2, 'TEST2', 2);