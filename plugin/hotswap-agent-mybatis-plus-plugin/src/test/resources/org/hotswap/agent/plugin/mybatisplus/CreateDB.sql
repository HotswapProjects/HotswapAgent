drop table users if exists;


create table users (
  id INT primary key ,
  name1  VARCHAR(32) NOT NULL,
  name2  VARCHAR(32) NOT NULL
);

insert into users (id, name1, name2) values(1, 'User1', 'User2');
insert into users (id, name1, name2) values(2, 'User2', 'User1');

