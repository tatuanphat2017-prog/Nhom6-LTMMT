-- =============================================================
-- CSDL cho Chat Nhom - Nhom 06
-- Nguoi phu trach: NGUYEN DINH QUOC BAO
--
-- Cach chay: mo file trong SSMS -> bam Execute (F5).
-- Chay lai nhieu lan van duoc: bang cu se bi xoa va tao lai (MAT DU LIEU CU).
--
-- Khop voi JdbcMessageStore (chat-server/.../server/store):
--   Users    : tai khoan (ten + mat khau da bam PBKDF2 + salt)
--   Rooms    : ten phong (duy nhat, khong phan biet hoa thuong), nguoi tao, ngay tao
--   Messages : phong, nguoi gui, thoi gian (ms), noi dung (NVARCHAR de luu tieng Viet),
--              thong tin file dinh kem (FileId, FileName, FileSize) neu la tin gui file
--   PrivateMessages : tin nhan rieng giua 2 nguoi (nguoi gui, nguoi nhan, thoi gian, noi dung, file)
-- =============================================================

-- 1. Tao database (chi tao neu chua co)
--    Collation Vietnamese_CI_AS: so sanh KHONG phan biet hoa/thuong (CI)
--    => "phong chung" va "Phong chung" la cung mot phong, dung voi giao thuc.
IF DB_ID(N'ChatNhom06') IS NULL
    CREATE DATABASE ChatNhom06 COLLATE Vietnamese_CI_AS;
GO

USE ChatNhom06;
GO

-- 2. Xoa bang cu (theo thu tu khoa ngoai: Messages truoc)
IF OBJECT_ID(N'dbo.PrivateMessages', N'U') IS NOT NULL DROP TABLE dbo.PrivateMessages;
IF OBJECT_ID(N'dbo.Messages', N'U') IS NOT NULL DROP TABLE dbo.Messages;
IF OBJECT_ID(N'dbo.Rooms', N'U')    IS NOT NULL DROP TABLE dbo.Rooms;
IF OBJECT_ID(N'dbo.Users', N'U')    IS NOT NULL DROP TABLE dbo.Users;
GO

-- 3. Bang Users
CREATE TABLE dbo.Users (
    UserId    INT IDENTITY(1,1) NOT NULL,
    Username  NVARCHAR(20)      NOT NULL,
    CreatedAt DATETIME2(0)      NOT NULL CONSTRAINT DF_Users_CreatedAt DEFAULT SYSDATETIME(),
    -- Mat khau KHONG luu truc tiep: PBKDF2-HMAC-SHA256 (65536 vong) + salt 16 byte, ma hoa Base64.
    -- NULL = ten chua dang ky (vd du lieu mau ben duoi): nguoi dau tien dang ky ten do se dat mat khau.
    PasswordHash VARCHAR(64)    NULL,
    PasswordSalt VARCHAR(32)    NULL,
    CONSTRAINT PK_Users PRIMARY KEY (UserId),
    CONSTRAINT UQ_Users_Username UNIQUE (Username)
);
GO

-- 4. Bang Rooms
--    CreatedBy luu TEN nguoi tao (khong phai khoa ngoai), vi phong mac dinh
--    "Phong chung" do server tao voi CreatedBy = 'server'.
CREATE TABLE dbo.Rooms (
    RoomId    INT IDENTITY(1,1) NOT NULL,
    RoomName  NVARCHAR(30)      NOT NULL,
    CreatedBy NVARCHAR(20)      NOT NULL,
    CreatedAt DATETIME2(0)      NOT NULL CONSTRAINT DF_Rooms_CreatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT PK_Rooms PRIMARY KEY (RoomId),
    CONSTRAINT UQ_Rooms_RoomName UNIQUE (RoomName)
);
GO

-- 5. Bang Messages
--    SentAt = milliseconds (System.currentTimeMillis() cua Java) nen dung BIGINT.
CREATE TABLE dbo.Messages (
    MessageId BIGINT IDENTITY(1,1) NOT NULL,
    RoomId    INT                  NOT NULL,
    Sender    NVARCHAR(20)         NOT NULL,
    SentAt    BIGINT               NOT NULL,
    Content   NVARCHAR(1000)       NOT NULL,
    -- Tin gui file: 3 cot duoi co gia tri, Content = N'[File] ten file'
    -- (noi dung file nam trong thu muc uploads/ cua server, ten = FileId). Tin chu: NULL.
    FileId    CHAR(32)             NULL,
    FileName  NVARCHAR(255)        NULL,
    FileSize  BIGINT               NULL,
    CONSTRAINT PK_Messages PRIMARY KEY (MessageId),
    CONSTRAINT FK_Messages_Rooms FOREIGN KEY (RoomId)
        REFERENCES dbo.Rooms (RoomId) ON DELETE CASCADE
);
GO

-- 5b. Bang PrivateMessages: tin nhan rieng giua 2 nguoi (Sender -> Receiver)
--     Luu ten (khong phai khoa ngoai) giong Messages.Sender. 3 cot File* nhu bang Messages.
CREATE TABLE dbo.PrivateMessages (
    PmId      BIGINT IDENTITY(1,1) NOT NULL,
    Sender    NVARCHAR(20)         NOT NULL,
    Receiver  NVARCHAR(20)         NOT NULL,
    SentAt    BIGINT               NOT NULL,
    Content   NVARCHAR(1000)       NOT NULL,
    FileId    CHAR(32)             NULL,
    FileName  NVARCHAR(255)        NULL,
    FileSize  BIGINT               NULL,
    -- 0 = nguoi nhan chua mo khung chat (client hien so do), 1 = da doc
    IsRead    BIT                  NOT NULL CONSTRAINT DF_PrivateMessages_IsRead DEFAULT 0,
    CONSTRAINT PK_PrivateMessages PRIMARY KEY (PmId)
);
GO
CREATE INDEX IX_PrivateMessages_Pair ON dbo.PrivateMessages (Sender, Receiver, PmId);
CREATE INDEX IX_PrivateMessages_Unread ON dbo.PrivateMessages (Receiver, IsRead);
GO

-- 6. Index phuc vu cau lay lich su: WHERE RoomId = ? ORDER BY MessageId DESC
CREATE INDEX IX_Messages_RoomId_MessageId ON dbo.Messages (RoomId, MessageId);
GO

-- 7. Du lieu mau
INSERT INTO dbo.Users (Username) VALUES (N'Phat'), (N'Bao'), (N'Lang'), (N'Huy');

INSERT INTO dbo.Rooms (RoomName, CreatedBy) VALUES (N'Phong chung', N'server');

DECLARE @roomId INT = (SELECT RoomId FROM dbo.Rooms WHERE RoomName = N'Phong chung');
-- Thoi gian mau: 03/10/2026 luc 11:00 gio Viet Nam (milliseconds)
INSERT INTO dbo.Messages (RoomId, Sender, SentAt, Content) VALUES
    (@roomId, N'Phat', 1791000000000, N'Chào cả nhóm, đây là phòng chung'),
    (@roomId, N'Bao',  1791000060000, N'CSDL đã tạo xong'),
    (@roomId, N'Lang', 1791000120000, N'Đã nối JDBC, test lịch sử nhé'),
    (@roomId, N'Huy',  1791000180000, N'Ok, mình đang làm giao diện');
GO

-- 8. Kiem tra nhanh
SELECT * FROM dbo.Users;
SELECT * FROM dbo.Rooms;
SELECT m.MessageId, r.RoomName, m.Sender, m.SentAt, m.Content
FROM dbo.Messages m JOIN dbo.Rooms r ON r.RoomId = m.RoomId
ORDER BY m.MessageId;
GO
