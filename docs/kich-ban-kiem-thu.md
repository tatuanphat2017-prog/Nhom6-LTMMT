# Kịch bản kiểm thử – Nhóm 06 (Ứng dụng Chat Nhóm)

> Người phụ trách: **Nguyễn Đình Quốc Bảo**.
> Công cụ: `ServerAdminMain` (giao diện quản trị) + `TestClient` / `ConsoleDemo` (client console). Mở 2–3 client để quan sát nhau.
> Ký hiệu: **A** = client thứ nhất, **B** = client thứ hai. Lệnh gõ trong `TestClient`.

**Cột "Kết quả thực tế"** đã điền theo lần chạy kiểm thử tự động ngày 13/10/2026 (server thật, client gửi đúng gói tin của giao thức).
Khi demo, nhóm chạy lại bằng tay theo cột "Bước làm", chụp ảnh và sửa cột này nếu khác. Riêng **TC18** (rút mạng thật giữa 2 máy) và **TC20** (với SQL Server thật) bắt buộc chạy lại bằng tay.

| STT | Tình huống | Bước làm | Kết quả mong đợi | Kết quả thực tế |
|---|---|---|---|---|
| 1 | Đăng nhập hợp lệ | Start server. A: `/login Lang` | A nhận `LOGIN_OK|Lang` rồi `ROOM_LIST`. Giao diện quản trị: "Người online" có Lang, "Kết nối" = 1 | Đạt. A nhận `LOGIN_OK|Lang` ; `ROOM_LIST|Phong chung:0` |
| 2 | Tên đăng nhập trùng (khác hoa/thường) | A đã đăng nhập "Lang". B: `/login lang` | B nhận `ERROR|NAME_TAKEN`, vẫn ở màn hình đăng nhập | Đạt. `ERROR|NAME_TAKEN|Ten 'lang' dang co nguoi su dung` |
| 3 | Tên sai định dạng | B: `/login Lang Tran` (có khoảng trắng); `/login` + 21 ký tự | Cả hai lần nhận `ERROR|INVALID_NAME` | Đạt. Cả 2 lần nhận `ERROR|INVALID_NAME|Ten 1-20 ky tu, chi gom chu, so, _ . - (khong khoang trang)` |
| 4 | Gửi lệnh khi chưa đăng nhập | Client mới kết nối: `/join Phong chung` | `ERROR|NOT_LOGGED_IN` | Đạt. `ERROR|NOT_LOGGED_IN|Ban can dang nhap truoc` |
| 5 | Đăng nhập lần thứ hai | A đã đăng nhập: `/login Lang2` | `ERROR|ALREADY_LOGGED_IN`, tên vẫn là Lang | Đạt. `ERROR|ALREADY_LOGGED_IN|Ban da dang nhap voi ten Lang` |
| 6 | Tạo phòng mới (tiếng Việt) | A: `/create Phòng AI` | A nhận `ROOM_CREATED`. Mọi người nhận `ROOM_LIST` có "Phòng AI:0". Bảng phòng trên giao diện quản trị thêm 1 dòng | Đạt. A nhận `ROOM_CREATED|Phòng AI` ; B nhận `ROOM_LIST|Phong chung:0|Phòng AI:0` |
| 7 | Tạo phòng trùng tên | A: `/create phòng ai` | `ERROR|ROOM_EXISTS` (không phân biệt hoa/thường) | Đạt. `ERROR|ROOM_EXISTS|Phong 'phòng ai' da ton tai` |
| 8 | Tên phòng sai định dạng | A: `/create` + 31 ký tự | `ERROR|INVALID_ROOM` | Đạt. `ERROR|INVALID_ROOM|Ten phong 1-30 ky tu, ...` |
| 9 | Vào phòng | A: `/join phong chung` (viết thường). Sau đó B: `/join Phong chung` | A nhận lần lượt `JOINED` → các `HISTORY_ITEM` → `HISTORY_END` → `MEMBERS`. Khi B vào, A nhận `USER_JOINED|Phong chung|Huy`. Số người trong bảng phòng tăng | Đạt. `JOINED|Phong chung` ; `HISTORY_END|Phong chung|0` ; `MEMBERS|Phong chung|Lang` ; A nhận `USER_JOINED|Phong chung|Huy` |
| 10 | Vào phòng không tồn tại | A: `/join Khong co` | `ERROR|ROOM_NOT_FOUND` | Đạt. `ERROR|ROOM_NOT_FOUND|Khong tim thay phong 'Khong co'` |
| 11 | Vào lại phòng đang ở | A đang ở "Phong chung": `/join Phong chung` | `ERROR|ALREADY_IN_ROOM` | Đạt. `ERROR|ALREADY_IN_ROOM|Ban dang o trong phong nay` |
| 12 | Chuyển phòng | A đang ở "Phong chung": `/join Phòng AI` | A nhận `JOINED|Phòng AI`. B (ở Phong chung) nhận `USER_LEFT` của A. Mỗi người chỉ ở 1 phòng | Đạt. A nhận `JOINED|Phòng AI` ; B nhận `USER_LEFT|Phong chung|Lang` |
| 13 | Rời phòng | A: `/leave` | A nhận `LEFT`. Người cùng phòng nhận `USER_LEFT`. Số người trong bảng phòng giảm | Đạt. A nhận `LEFT|Phòng AI` |
| 14 | Gửi tin khi chưa vào phòng | A chưa vào phòng nào, gửi gói `MSG|Phong chung|hello` (`/raw MSG|Phong chung|hello`) | `ERROR|NOT_IN_ROOM` | Đạt. `ERROR|NOT_IN_ROOM|Ban phai vao phong truoc khi gui tin` |
| 15 | Tin nhắn rỗng | A trong phòng, gửi tin chỉ có khoảng trắng (`/raw MSG|Phong chung|   `) | `ERROR|EMPTY_MESSAGE`, không ai nhận tin | Đạt. `ERROR|EMPTY_MESSAGE|Tin nhan trong` |
| 16 | Tin nhắn quá dài | A gửi tin 1001 ký tự | `ERROR|MESSAGE_TOO_LONG` | Đạt. `ERROR|MESSAGE_TOO_LONG|Tin nhan toi da 1000 ky tu` |
| 17 | Tin có ký tự đặc biệt và tiếng Việt | A gửi: `Chào | a\b` + xuống dòng + `dòng 2` (qua giao diện của Huy; client console không gõ được xuống dòng) | B nhận đúng nguyên văn, kể cả dấu `|`, `\`, xuống dòng và chữ có dấu | Đạt. Gói tin `MSG|Phong chung|Lang|...|Chào \p a\\b\ndòng 2`, giải mã ra đúng nguyên văn |
| 18 | Rút mạng / tắt client giữa chừng | A và B cùng phòng. Tắt hẳn cửa sổ B (hoặc rút dây mạng / tắt Wi-Fi máy B) không đăng xuất | A nhận `USER_LEFT` của B. Giao diện quản trị: B biến mất khỏi "Người online", số kết nối giảm. B đăng nhập lại được bằng tên cũ | Đạt (đóng socket đột ngột). A nhận `USER_LEFT|Phong chung|Huy` ; online còn `[Lang]` ; B đăng nhập lại nhận `LOGIN_OK|Huy`. *Cần thử lại bằng rút mạng thật giữa 2 máy* |
| 19 | Đăng xuất và dừng server | A: `/quit`. Sau đó bấm **Stop** trên giao diện quản trị khi còn client B | A nhận `BYE|Tam biet` rồi bị đóng kết nối. Khi Stop, B nhận `BYE|Server dung hoat dong`. Danh sách online và bảng phòng được xóa | Đạt. `BYE|Tam biet` ; `BYE|Server dung hoat dong`, kết nối đóng sau BYE |
| 20 | Tắt server mở lại còn lịch sử | Có `db.properties` (SQL Server). Chat vài câu → Stop → Start → client mới `/join Phong chung` | Phòng đã tạo vẫn còn trong `ROOM_LIST`. Client nhận lại các tin cũ qua `HISTORY_ITEM` theo thứ tự cũ → mới | Đạt (CSDL mô phỏng chế độ SQL Server). `ROOM_LIST|Phong chung:0|Phòng AI:0` ; `HISTORY_ITEM|...|Chào \p a\\b\ndòng 2` ; `HISTORY_END|Phong chung|1`. *Cần chạy lại với SQL Server thật* |

## Truyền file (giới hạn 25 MB)

Cột "Kết quả thực tế" điền theo lần chạy kiểm thử tự động ngày 20/10/2026 (server thật, 2–3 client thật, so sánh file gửi và file tải về bằng SHA-256). Khi demo, làm lại bằng giao diện: bấm **Gửi file**, rồi nhấp vào tên file trong khung chat để tải.

| STT | Tình huống | Bước làm | Kết quả mong đợi | Kết quả thực tế |
|---|---|---|---|---|
| 21 | Gửi file thường (tên tiếng Việt) | A bấm **Gửi file**, chọn file ~1 MB "Báo cáo nhóm 6.pdf" | Cả phòng (kể cả A) thấy dòng "đã gửi file Báo cáo nhóm 6.pdf (976.6 KB)" | Đạt. B nhận `FILE|Phong chung|An|...|Báo cáo nhóm 6.pdf|1000000` |
| 22 | Tải file về | B nhấp vào tên file, chọn nơi lưu | Thanh tiến trình chạy tới 100%, hỏi "Mở file / Mở thư mục". File mở được bình thường | Đạt. File tải về giống hệt file gốc (SHA-256 trùng) |
| 23 | Gửi file đúng 25 MB | A gửi file 26.214.400 byte | Gửi được, B tải về đầy đủ | Đạt. Gửi 1,2 giây, tải 0,3 giây (mạng nội bộ), SHA-256 trùng |
| 24 | File vượt 25 MB | A chọn file 25 MB + 1 byte | Báo "File quá lớn... Tối đa 25.0 MB", không gửi gì lên mạng | Đạt. Client chặn ngay, server không nhận gói nào |
| 25 | File rỗng | A chọn file 0 byte | Báo "File rỗng (0 byte), không thể gửi" | Đạt |
| 26 | Client gian lận khai 10 GB | Gửi gói `FILE_START|x1|Phong chung|virus.iso|10737418240` | Server từ chối, không tạo file | Đạt. `FILE_REJECT|x1|FILE_TOO_LARGE|File vuot qua gioi han 25.0 MB` |
| 27 | Gửi dư byte so với khai báo | Khai 10 byte rồi gửi mảnh 4096 byte | Server hủy, xóa file tạm | Đạt. `FILE_REJECT|x2|FILE_TOO_LARGE|Du lieu gui len nhieu hon dung luong da khai bao` |
| 28 | Gửi thiếu byte / dữ liệu lỗi | Khai 100 byte, gửi 50 byte rồi `FILE_END`; gửi mảnh `@@@@` | Server từ chối cả hai | Đạt. `FILE_INVALID` (thiếu 50/100 byte) ; `FILE_INVALID` (dữ liệu file bị lỗi) |
| 29 | Đọc trộm file ngoài thư mục | `FILE_GET|../../../etc/passwd` | Từ chối | Đạt. `FILE_DATA_ERROR|...|FILE_NOT_FOUND|Ma file khong hop le` |
| 30 | Tên file chứa đường dẫn | Gửi file tên `..\..\Windows\evil.exe` | Tên được làm sạch thành `evil.exe` | Đạt. `FILE|...|evil.exe|3` |
| 31 | Chat trong lúc đang gửi/tải file lớn | A gửi file 25 MB, B chat cùng lúc; sau đó B tải file 25 MB, A chat cùng lúc | Tin chat vẫn tới ngay, không phải đợi file xong | Đạt. Tin chat tới sau 6 ms trong khi file vẫn đang gửi |
| 32 | Hủy giữa chừng | A gửi file 25 MB rồi bấm **Hủy** | Báo "Đã hủy gửi file", server không còn file tạm | Đạt. Thư mục uploads không còn file `.part` |
| 33 | Mất kết nối giữa chừng | Tắt client khi đang gửi; tắt client khi đang tải | Server xóa file tạm. Bên tải báo "Mất kết nối với server" | Đạt. 0 file `.part` trên server ; client báo lỗi, không treo |
| 34 | Người vào sau tải file trong lịch sử | C vào phòng sau khi A đã gửi file | Lịch sử có các dòng file, C nhấp tải được | Đạt. Nhận 3 `HISTORY_FILE`, tải về SHA-256 trùng |
| 35 | Tải 2 file cùng lúc | C tải file thứ nhất, chưa xong đã tải file thứ hai | File 1 tải xong, file 2 báo đang bận | Đạt. "Đang có một file khác được truyền, hãy đợi xong" |
| 36 | Lưu thông tin file vào SQL Server | Gửi file → mở SSMS, xem bảng `Messages` | Dòng mới có `Content = [File] tên file`, các cột `FileId`, `FileName`, `FileSize` có giá trị | *Cần chạy với SQL Server thật* |

## Người online và tin nhắn riêng

Cột "Kết quả thực tế" điền theo lần chạy kiểm thử tự động ngày 20/10/2026: 3 client thật qua giao thức, và 1 client có giao diện thật được điều khiển bằng chuột giả lập.

| STT | Tình huống | Bước làm | Kết quả mong đợi | Kết quả thực tế |
|---|---|---|---|---|
| 37 | Danh sách online | Phat đăng nhập, sau đó Lang, Huy đăng nhập | Cột trái của mọi người cập nhật ngay, mình đứng đầu kèm "(bạn)" | Đạt. `ONLINE_LIST|Lang|Phat`, rồi `ONLINE_LIST|Huy|Lang|Phat` |
| 38 | Người thoát | Huy tắt client | Huy biến mất khỏi cột trái của mọi người | Đạt |
| 39 | Mở chat riêng | Bấm vào "Lang" ở cột trái | Khung giữa đổi thành "Tin nhắn riêng với Lang", tải lịch sử | Đạt |
| 40 | Gửi tin riêng | Phat gửi `Chào Lãng | test` + xuống dòng | Chỉ Phat và Lang thấy, Huy không nhận gì | Đạt. Huy không nhận gói nào trong 0,8 giây |
| 41 | Tin chưa đọc | Bình đang xem phòng, An gửi 3 tin riêng | Cạnh tên An hiện số đỏ "3", tin không lẫn vào khung phòng | Đạt |
| 42 | Phòng có tin khi đang chat riêng | Bình đang chat riêng, Huy chat trong phòng | Tên phòng ở cột phải hiện số đỏ "1". Bấm tên phòng thì quay lại khung phòng, thấy tin | Đạt |
| 43 | Gửi file riêng | Trong chat riêng, bấm Gửi file (300 KB) | Chỉ 2 người thấy dòng file, người nhận tải về giống hệt | Đạt. Huy không nhận ; file tải về trùng từng byte |
| 44 | File riêng > 25 MB | Chọn file 25 MB + 1 byte | Báo quá lớn, không gửi | Đạt |
| 45 | Gửi cho chính mình / người offline | `PM|Phat|...` từ Phat ; `PM|KhongCo|...` | `SELF_MESSAGE` ; `USER_NOT_FOUND` | Đạt |
| 46 | Người kia offline khi đang chat riêng | Đang chat với Huy thì Huy thoát | Hiện "Huy đã offline", khóa ô nhập, vẫn xem được tin cũ | Đạt |
| 47 | Lịch sử tin riêng sau khi đăng nhập lại | Lang thoát rồi đăng nhập lại bằng "lang", mở chat với Phat | Thấy đủ tin cũ của cả 2 chiều, kể cả tin file | Đạt. `PM_HISTORY_END|PHAT|3` |
| 48 | Người thứ ba xem trộm lịch sử | Huy gửi `PM_HISTORY|Phat|50` | Không thấy tin của Phat với Lang | Đạt. `PM_HISTORY_END|Phat|0` |
| 49 | Nháy đúp thành viên phòng | Nháy đúp "Huy" trong khung Thành viên phòng | Mở chat riêng với Huy | Đạt |
| 50 | Lưu tin riêng vào SQL Server | Gửi vài tin riêng rồi mở SSMS xem bảng `PrivateMessages` | Có đủ các dòng, đúng người gửi/nhận | *Cần chạy với SQL Server thật* |

## Đăng ký / đăng nhập có mật khẩu

Cột "Kết quả thực tế" điền theo lần chạy kiểm thử tự động ngày 20/10/2026: kiểm thử qua giao thức, và màn hình đăng nhập thật được điều khiển tự động. **Lưu ý:** từ bản này, các bước `/login ten` ở TC1–TC5 đổi thành `/register ten matKhau` (lần đầu) hoặc `/login ten matKhau`.

| STT | Tình huống | Bước làm | Kết quả mong đợi | Kết quả thực tế |
|---|---|---|---|---|
| 51 | Đăng ký thành công | Bấm "Đăng ký", nhập tên Phát, mật khẩu 2 lần giống nhau | Vào thẳng màn hình chat | Đạt. `LOGIN_OK|Phát` |
| 52 | Mật khẩu ngắn / 2 mật khẩu khác nhau | Mật khẩu `123`; hoặc nhập lại khác | Báo lỗi ngay, không gửi lên server | Đạt. "Mật khẩu phải từ 6…" ; "Hai mật khẩu không giống nhau" |
| 53 | Đăng ký trùng tên | Đăng ký `PHÁT` khi đã có `Phát` | Báo tên đã có người đăng ký | Đạt. `ERROR|USER_EXISTS` |
| 54 | Sai mật khẩu | Đăng nhập Phát với mật khẩu sai | "Sai tên đăng nhập hoặc mật khẩu" | Đạt |
| 55 | Tên không tồn tại | Đăng nhập `KhongCo` | Báo y hệt sai mật khẩu, thời gian trả lời như nhau | Đạt. Cùng `WRONG_LOGIN`, 48 ms / 48 ms |
| 56 | Sai 5 lần | Đăng nhập sai 5 lần liên tiếp | Server ngắt kết nối | Đạt. `BYE|Sai mat khau qua 5 lan, ket noi bi dong` |
| 57 | Đăng nhập trùng | Phát đang online, máy khác đăng nhập Phát đúng mật khẩu | "Tài khoản đang đăng nhập ở máy khác" | Đạt. `ERROR|NAME_TAKEN` |
| 58 | Không phân biệt hoa/thường | Đăng ký `Lang`, đăng nhập bằng `lang` | Vào được, tên hiển thị `Lang` | Đạt. `LOGIN_OK|Lang` |
| 59 | Mật khẩu có ký tự đặc biệt | Mật khẩu có dấu tiếng Việt, khoảng trắng | Đăng ký và đăng nhập lại đúng | Đạt |
| 60 | Mật khẩu không lưu dạng chữ | Mở SSMS: `SELECT Username, PasswordHash, PasswordSalt FROM Users` | Chỉ thấy chuỗi băm 44 ký tự và salt 24 ký tự, 2 người cùng mật khẩu ra 2 chuỗi khác nhau | Đạt (lưu trong RAM). *Cần xem lại trên SQL Server thật* |
| 61 | Ghi nhớ IP và tên | Đăng nhập xong, tắt client rồi mở lại | IP, cổng, tên đã điền sẵn, ô mật khẩu trống | *Cần thử trên máy thật* |

## Nhắn tin cho người offline và tin chưa đọc

Cột "Kết quả thực tế" điền theo lần chạy kiểm thử tự động ngày 21/10/2026.

| STT | Tình huống | Bước làm | Kết quả mong đợi | Kết quả thực tế |
|---|---|---|---|---|
| 62 | Danh sách mọi người | Có 3 tài khoản Binh, An, Cuong; Cuong offline. Binh đăng nhập | Cột trái: Binh (bạn), An (chấm xanh), Cuong (chấm xám, "offline") | Đạt |
| 63 | Tài khoản mới | Huy vừa đăng ký | Người đang online thấy Huy xuất hiện ngay trong cột trái | Đạt. `USER_LIST|Huy|Phat` |
| 64 | Nhắn cho người offline | Huy offline. Phat gửi 3 tin + 1 file cho Huy | Gửi được, Phat thấy 4 tin trong khung chat | Đạt |
| 65 | Số đỏ khi đăng nhập | Huy đăng nhập lại | Cạnh tên Phat hiện số đỏ **4**, bấm vào thấy đủ 4 tin, tải được file | Đạt. `UNREAD_LIST|Phat:4` |
| 66 | Chưa mở thì vẫn còn | Huy đăng nhập nhưng không bấm vào Phat, đăng xuất rồi vào lại | Vẫn còn số 4 | Đạt |
| 67 | Mở rồi thì hết | Huy bấm vào Phat, rồi đăng xuất và vào lại | Không còn số đỏ (chỉ còn tin mới gửi sau đó) | Đạt. Còn đúng 1 tin mới |
| 68 | Đang xem thì không tính | Huy đang mở khung chat với Phat, Phat gửi tiếp | Tin hiện ngay, không có số đỏ | Đạt |
| 69 | Đếm riêng từng người | Phat gửi cho Bao | Số chưa đọc của Huy không đổi | Đạt |
| 70 | Tên không tồn tại | `PM|KhongCo|x` | `ERROR|USER_NOT_FOUND` | Đạt |
| 71 | Kiểm tra trong SQL Server | Sau TC64, SSMS: `SELECT Sender, Receiver, Content, IsRead FROM PrivateMessages` | 4 dòng `IsRead = 0`; sau TC67 thành `1` | *Cần chạy với SQL Server thật* |

## Đã xem và tin chưa đọc của phòng

Cột "Kết quả thực tế" điền theo lần chạy kiểm thử tự động ngày 21/10/2026: 4 client qua giao thức, và 1 client có giao diện thật.

| STT | Tình huống | Bước làm | Kết quả mong đợi | Kết quả thực tế |
|---|---|---|---|---|
| 72 | Đã xem trong phòng | An gửi tin, Bình đang mở khung phòng | Dưới khung chat của An hiện "✓ Đã xem: Binh" | Đạt |
| 73 | Nhiều người xem | Cường vào phòng, sau đó Bình đọc tiếp | "✓ Đã xem: Binh, Cuong", không có tên người gửi | Đạt |
| 74 | Số đỏ phòng khác | Bình từng vào "Nhom A" rồi chuyển sang "Phong chung", An gửi 2 tin vào Nhom A | Cạnh "Nhom A" hiện số đỏ 2 | Đạt |
| 75 | Số đỏ phòng khi offline | Cường thoát, An gửi thêm tin, Cường đăng nhập lại | "Phong chung" hiện số đỏ 3 | Đạt. `ROOM_UNREAD_LIST|Phong chung:3` |
| 76 | Vào phòng thì hết số | Cường vào Phong chung | Số đỏ mất | Đạt |
| 77 | Phòng chưa từng vào | Dũng chưa vào phòng nào | Không hiện số đỏ ở phòng nào | Đạt |
| 78 | Tin mình gửi | An tự gửi tin | Không tính là tin chưa đọc của An | Đạt |
| 79 | Đã xem tin riêng | Bình gửi tin riêng cho An, An mở khung chat | Bên Bình hiện "✓ Đã xem" ngay | Đạt |
| 80 | Đã xem sau khi đăng nhập lại | An đăng xuất rồi vào lại, mở chat với Bình | Vẫn thấy "✓ Đã xem" dưới tin cuối mình gửi | Đạt |
| 81 | Tin mới chưa xem | Gửi thêm tin riêng mới | Dòng "Đã xem" biến mất cho tới khi người kia mở | Đạt |
| 82 | Kiểm tra SQL Server | SSMS: `SELECT * FROM RoomReads` | Mỗi người từng vào phòng có 1 dòng, `LastReadId` tăng khi đọc | *Cần chạy với SQL Server thật* |
