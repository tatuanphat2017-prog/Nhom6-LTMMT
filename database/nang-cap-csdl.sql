-- =============================================================
-- Nang cap CSDL ChatNhom06 (tao tu schema.sql ban cu) de dung:
--   - gui file trong phong  : them 3 cot FileId, FileName, FileSize vao Messages
--   - tin nhan rieng        : tao bang PrivateMessages
--   - dang nhap co mat khau : them 2 cot PasswordHash, PasswordSalt vao Users
--   - tin chua doc          : them cot IsRead vao PrivateMessages (tin cu coi nhu da doc)
--   - da xem / chua doc phong : tao bang RoomReads
-- KHONG xoa du lieu cu. Chay nhieu lan cung khong sao.
--
-- Server cung tu chay cac lenh nay khi khoi dong; file nay dung khi tai khoan
-- trong db.properties khong du quyen ALTER/CREATE TABLE.
-- =============================================================
USE ChatNhom06;
GO

IF COL_LENGTH('dbo.Messages', 'FileId') IS NULL
    ALTER TABLE dbo.Messages ADD
        FileId   CHAR(32)      NULL,
        FileName NVARCHAR(255) NULL,
        FileSize BIGINT        NULL;
GO

IF OBJECT_ID(N'dbo.PrivateMessages', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.PrivateMessages (
        PmId     BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_PrivateMessages PRIMARY KEY,
        Sender   NVARCHAR(20)   NOT NULL,
        Receiver NVARCHAR(20)   NOT NULL,
        SentAt   BIGINT         NOT NULL,
        Content  NVARCHAR(1000) NOT NULL,
        FileId   CHAR(32)       NULL,
        FileName NVARCHAR(255)  NULL,
        FileSize BIGINT         NULL
    );
    CREATE INDEX IX_PrivateMessages_Pair ON dbo.PrivateMessages (Sender, Receiver, PmId);
END
GO

IF COL_LENGTH('dbo.Users', 'PasswordHash') IS NULL
    ALTER TABLE dbo.Users ADD
        PasswordHash VARCHAR(64) NULL,
        PasswordSalt VARCHAR(32) NULL;
GO

IF COL_LENGTH('dbo.PrivateMessages', 'IsRead') IS NULL
BEGIN
    ALTER TABLE dbo.PrivateMessages ADD IsRead BIT NOT NULL
        CONSTRAINT DF_PrivateMessages_IsRead DEFAULT 0;
    EXEC('UPDATE dbo.PrivateMessages SET IsRead = 1');
END
GO

IF OBJECT_ID(N'dbo.RoomReads', N'U') IS NULL
    CREATE TABLE dbo.RoomReads (
        Username   NVARCHAR(20) NOT NULL,
        RoomId     INT          NOT NULL,
        LastReadId BIGINT       NOT NULL,
        CONSTRAINT PK_RoomReads PRIMARY KEY (Username, RoomId),
        CONSTRAINT FK_RoomReads_Rooms FOREIGN KEY (RoomId)
            REFERENCES dbo.Rooms (RoomId) ON DELETE CASCADE
    );
GO

-- Kiem tra
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME IN ('Users', 'Messages', 'PrivateMessages', 'RoomReads') ORDER BY TABLE_NAME, ORDINAL_POSITION;
