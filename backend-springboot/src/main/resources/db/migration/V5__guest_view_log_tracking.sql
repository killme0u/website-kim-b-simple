-- V5__guest_view_log_tracking.sql
-- F-602: 비회원 조회수 중복 방지 - IP 주소 기반 추적
-- member_id를 nullable로 변경하고 ip_address 컬럼 추가

-- 기존 primary key constraint 제거
ALTER TABLE post_view_log DROP CONSTRAINT post_view_log_pkey;

-- member_id nullable로 변경
ALTER TABLE post_view_log ALTER COLUMN member_id DROP NOT NULL;

-- ip_address 컬럼 추가 (IPv6 지원)
ALTER TABLE post_view_log ADD COLUMN ip_address VARCHAR(45);

-- 회원용: post_id + member_id + viewed_on (unique)
CREATE UNIQUE INDEX idx_post_view_log_member
  ON post_view_log(post_id, member_id, viewed_on)
  WHERE member_id IS NOT NULL;

-- 비회원용: post_id + ip_address + viewed_on (unique)
CREATE UNIQUE INDEX idx_post_view_log_guest
  ON post_view_log(post_id, ip_address, viewed_on)
  WHERE member_id IS NULL AND ip_address IS NOT NULL;

-- 재조회 성능 인덱스
CREATE INDEX idx_post_view_log_lookup
  ON post_view_log(post_id, viewed_on);
