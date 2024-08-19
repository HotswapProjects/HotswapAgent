ALTER TABLE users ADD COLUMN gender VARCHAR(255) DEFAULT 'unknown';
update users set gender='male' where id=1;
update users set gender='female' where id=2;