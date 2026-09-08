-- V1__init.sql
CREATE TABLE member (
                        id                   BIGSERIAL PRIMARY KEY,
                        username             VARCHAR(30)  NOT NULL UNIQUE,
                        password_hash        VARCHAR(100) NOT NULL,
                        name                 VARCHAR(50)  NOT NULL,
                        email                VARCHAR(255) NOT NULL UNIQUE,
                        phone                VARCHAR(20)  NOT NULL,
                        status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                        role                 VARCHAR(20)  NOT NULL DEFAULT 'USER',
                        must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
                        temp_password_expires_at TIMESTAMPTZ,
                        created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
                        updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE verification_token (
                                    id         BIGSERIAL PRIMARY KEY,
                                    member_id  BIGINT      NOT NULL REFERENCES member(id) ON DELETE CASCADE,
                                    token_hash VARCHAR(64) NOT NULL UNIQUE,
                                    purpose    VARCHAR(30) NOT NULL,
                                    expires_at TIMESTAMPTZ NOT NULL,
                                    used_at    TIMESTAMPTZ
);
CREATE INDEX idx_token_member ON verification_token (member_id, purpose);

CREATE TABLE board (
                       id                      BIGSERIAL PRIMARY KEY,
                       slug                    VARCHAR(30) NOT NULL UNIQUE,
                       name                    VARCHAR(50) NOT NULL,
                       requires_auth_to_read   BOOLEAN NOT NULL DEFAULT FALSE,
                       requires_auth_to_write  BOOLEAN NOT NULL,
                       allows_comment          BOOLEAN NOT NULL,
                       allows_attachment       BOOLEAN NOT NULL,
                       display_order           INT     NOT NULL DEFAULT 0
);

CREATE TABLE post (
                      id                  BIGSERIAL PRIMARY KEY,
                      board_id            BIGINT NOT NULL REFERENCES board(id),
                      member_id           BIGINT     REFERENCES member(id),
                      guest_nickname      VARCHAR(30),
                      guest_password_hash VARCHAR(100),
                      title               VARCHAR(200) NOT NULL,
                      content             TEXT         NOT NULL,
                      view_count          INT NOT NULL DEFAULT 0,
                      like_count          INT NOT NULL DEFAULT 0,
                      created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
                      updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
                      deleted_at          TIMESTAMPTZ,
                      CONSTRAINT post_author_ck CHECK (
                          (member_id IS NOT NULL
                              AND guest_nickname IS NULL AND guest_password_hash IS NULL)
                              OR
                          (member_id IS NULL
                              AND guest_nickname IS NOT NULL AND guest_password_hash IS NOT NULL)
                          )
);
CREATE INDEX idx_post_list ON post (board_id, deleted_at, id DESC);

CREATE TABLE comment (
                         id         BIGSERIAL PRIMARY KEY,
                         post_id    BIGINT NOT NULL REFERENCES post(id),
                         member_id  BIGINT NOT NULL REFERENCES member(id),
                         content    TEXT   NOT NULL,
                         created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                         updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                         deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_comment_post ON comment (post_id, deleted_at, id);

CREATE TABLE attachment (
                            id            BIGSERIAL PRIMARY KEY,
                            post_id       BIGINT NOT NULL REFERENCES post(id) ON DELETE CASCADE,
                            original_name VARCHAR(255) NOT NULL,
                            stored_name   VARCHAR(100) NOT NULL UNIQUE,
                            content_type  VARCHAR(100) NOT NULL,
                            media_kind    VARCHAR(10)  NOT NULL,
                            byte_size     BIGINT       NOT NULL,
                            created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_attachment_post ON attachment (post_id);

CREATE TABLE post_like (
                           post_id    BIGINT NOT NULL REFERENCES post(id) ON DELETE CASCADE,
                           member_id  BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
                           created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                           PRIMARY KEY (post_id, member_id)
);

CREATE TABLE post_view_log (
                               post_id    BIGINT NOT NULL REFERENCES post(id) ON DELETE CASCADE,
                               member_id  BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
                               viewed_on  DATE   NOT NULL,
                               created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                               PRIMARY KEY (post_id, member_id, viewed_on)
);
CREATE INDEX idx_view_log_date ON post_view_log (viewed_on);
