CREATE TABLE boot_user
(
    id    INT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name  VARCHAR(32) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    email VARCHAR(32)
);

INSERT INTO boot_user(`id`, `name`, `phone`, `email`) VALUES (1, 'tom', '3311', '123456@gmail.com');
INSERT INTO boot_user(`id`, `name`, `phone`, `email`) VALUES (2, 'jim', '1122', '4562@gmail.com');
INSERT INTO boot_user(`id`, `name`, `phone`, `email`) VALUES (3, 'bb', '223', 'bb@gmail.com');
INSERT INTO boot_user(`id`, `name`, `phone`, `email`) VALUES (4, 'dd', '5544', 'dd@gmail.com');