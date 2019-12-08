drop table users if exists;

create table users (
  id int,
  name varchar(20)
);

insert into users (id, name) values(1, 'User1');
