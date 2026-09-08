ALTER TABLE member
    ADD COLUMN nickname VARCHAR(30);

CREATE UNIQUE INDEX ux_member_nickname
    ON member (nickname)
    WHERE nickname IS NOT NULL;
