drop table users if exists;

create table users (
  id int,
  name1 varchar(20),
  name2 varchar(20),
);

insert into users (id, name1, name2) values(1, 'User1', 'User2');
insert into users (id, name1, name2) values(1, 'User2', 'User1');

