export const navLinks = [
  { to: '/', label: 'Trang chủ' },
  { to: '/bo-mon', label: 'Bộ môn' },
  { to: '/huan-luyen-vien', label: 'Huấn luyện viên' },
  { to: '/co-so-vat-chat', label: 'Cơ sở vật chất' },
  { to: '/tuyen-sinh', label: 'Tuyển sinh' },
]

export const disciplines = [
  { code: 'GYM', title: 'Sức mạnh & hình thể', color: '#146edb' },
  { code: 'YOGA', title: 'Cân bằng & dẻo dai', color: '#0fb9e3' },
  { code: 'BƠI', title: 'Kỹ năng & sức bền', color: '#1fa36b' },
  { code: 'CẦU LÔNG', title: 'Nhanh & linh hoạt', color: '#f78c28' },
  { code: 'BÓNG RỔ', title: 'Đồng đội & bứt phá', color: '#0a1628' },
  { code: 'AEROBIC', title: 'Năng lượng & vui vẻ', color: '#146edb' },
]

export const coachesHome = [
  { initials: 'MA', name: 'Minh An', role: 'Strength Coach', desc: 'Mục tiêu hình thể', color: '#146edb' },
  { initials: 'TH', name: 'Thu Hà', role: 'Yoga Coach', desc: 'Cân bằng & linh hoạt', color: '#0fb9e3' },
  { initials: 'QH', name: 'Quang Huy', role: 'Swim Coach', desc: 'Kỹ thuật bơi', color: '#146edb' },
]

export const coachSpotlight = [
  {
    id: 1,
    name: 'Park Hang Seo',
    role: 'HLV trưởng môn bóng đá',
    sport: 'Bóng đá',
    img: '/images/coach-1.png',
    skills: ['Kỹ năng chuyền bóng', 'Sức bền', 'Kỹ thuật sút bóng'],
    bio: [
      'HLV Park Hang-seo là huấn luyện viên nhất lịch sử bóng đá Việt Nam, đưa đội tuyển bước vào kỷ nguyên hoàng kim rực rỡ. Ông đã gặt hái chiến tích lịch sử như Á quân U23 châu Á 2018, vô địch AFF Cup 2018 và vào tới vòng loại thứ ba World Cup.',
      'Bên cạnh triết lý bóng đá kỷ luật và lối chơi hiệu quả, ông còn chinh phục người hâm mộ bằng sự tâm huyết và tình cảm như một người cha dành cho học trò.',
    ],
    stats: [['100+', 'Số trận'], ['60+', 'Trận thắng'], ['5+', 'Huy chương']],
    achievements: ['Vô địch AFF Cup (2018)', 'Huy chương vàng SEA GAME', 'Á quân U23 Châu Á'],
  },
  ...[2, 3, 4, 5, 6].map((i) => ({
    id: i,
    name: `Huấn luyện viên ${i}`,
    role: 'HLV chuyên nghiệp',
    sport: 'Thể hình',
    img: `/images/coach-${i}.png`,
    skills: ['Kỹ thuật cơ bản', 'Sức mạnh', 'Thể lực'],
    bio: [
      'Huấn luyện viên giàu kinh nghiệm, xây dựng giáo án theo từng mục tiêu và thể trạng của học viên.',
      'Luôn đồng hành sát sao để học viên tiến bộ an toàn và bền vững.',
    ],
    stats: [['50+', 'Học viên'], ['30+', 'Khóa học'], ['3+', 'Giải thưởng']],
    achievements: ['Chứng chỉ huấn luyện quốc tế', 'Hơn 5 năm kinh nghiệm', 'Đào tạo nhiều học viên xuất sắc'],
  })),
]

export const timeline = [
  { year: '2018', title: 'Khởi đầu', text: 'Đặt nền móng thành lập trung tâm và đưa vào vận hành những lớp học thể thao đầu tiên, đáp ứng nhu cầu rèn luyện sức khỏe cơ bản của học viên.' },
  { year: '2020', title: 'Mở rộng', text: 'Đa dạng hóa thêm nhiều bộ môn thể thao mới, đồng thời tiến hành nâng cấp toàn bộ khu vực tập luyện hiện đại nhằm nâng cao trải nghiệm hội viên.' },
  { year: '2023', title: 'Phát triển', text: 'Mở rộng quy mô hệ thống huấn luyện chuyên nghiệp và triển khai các chương trình đào tạo chuyên sâu, giúp học viên phát triển kỹ năng một cách bài bản.' },
  { year: '2026', title: 'Hôm nay', text: 'Vững vàng khẳng định vị thế trung tâm hàng đầu, tiếp tục lan tỏa năng lượng tích cực và xây dựng một cộng đồng thể thao ngày càng năng động, khỏe mạnh.' },
]

export const coreValues = [
  { n: '01', title: 'Chuyên nghiệp', text: 'Quy tụ đội ngũ huấn luyện viên giàu kinh nghiệm cùng giáo trình đào tạo bài bản, chuẩn hóa, giúp học viên tiếp thu kỹ thuật chính xác và nâng cao phong độ nhanh chóng.', icon: 'bi-award' },
  { n: '02', title: 'Tận tâm', text: 'Luôn đặt trải nghiệm và sự tiến bộ của từng học viên làm trọng tâm, lắng nghe và đồng hành sát sao trong suốt quá trình tập luyện để đạt hiệu quả tối ưu.', icon: 'bi-bullseye' },
  { n: '03', title: 'Định hướng tập luyện rõ ràng', text: 'Thiết kế lộ trình huấn luyện khoa học với mục tiêu cụ thể theo từng giai đoạn, giúp học viên dễ dàng theo dõi sự phát triển và duy trì thói quen lâu dài.', icon: 'bi-layers' },
  { n: '04', title: 'Cơ sở vật chất', text: 'Hệ thống sân tập rộng rãi cùng trang thiết bị hiện đại chuẩn quốc tế, mang đến không gian rèn luyện an toàn, tiện nghi và truyền cảm hứng mỗi ngày.', icon: 'bi-grid' },
]

export const facilities = [
  {
    title: 'Sân vận động Center Sport',
    img: '/images/facility-1.png',
    text: 'Sở hữu hệ thống trang thiết bị đạt chuẩn FIBA, sân vận động MLC có diện tích 1.976m² và sức chứa 1.500 – 2.500 chỗ ngồi. Center Sports là đối tác chiến lược, sân nhà của đội tuyển bóng rổ Saigon Heat, tạo điều kiện giúp học viên đội tuyển có cơ hội tham dự các giải đấu trong hệ thống SSA, cũng như tham dự những trận đấu của Saigon Heat tổ chức tại Center Sports.',
  },
  {
    title: 'Phòng Gym',
    img: '/images/facility-2.png',
    text: 'Thiết bị sức mạnh và cardio hiện đại, không gian thoáng sạch, có huấn luyện viên hướng dẫn kỹ thuật cho mọi cấp độ.',
  },
  {
    title: 'Hồ bơi',
    img: '/images/facility-3.png',
    text: 'Hồ bơi tiêu chuẩn, nước được xử lý sạch sẽ mỗi ngày, có khu vực riêng cho lớp học kỹ thuật bơi.',
  },
  {
    title: 'Studio',
    img: '/images/facility-4.png',
    text: 'Phòng tập Yoga, dance và group class với sàn gỗ, gương lớn và hệ thống âm thanh chuyên dụng.',
  },
]

export const schedule = [
  { day: 'Thứ 2', slots: [['06:00', 'Yoga buổi sáng', 'Studio'], ['17:30', 'Gym cơ bản', 'Phòng Gym'], ['19:00', 'Bơi kỹ thuật', 'Hồ bơi']] },
  { day: 'Thứ 3', slots: [['06:30', 'Aerobic', 'Studio'], ['18:00', 'Cầu lông', 'Sân trong nhà'], ['19:30', 'Bóng rổ', 'Sân vận động']] },
  { day: 'Thứ 4', slots: [['06:00', 'Yoga buổi sáng', 'Studio'], ['17:30', 'Gym nâng cao', 'Phòng Gym'], ['19:00', 'Bơi sức bền', 'Hồ bơi']] },
  { day: 'Thứ 5', slots: [['06:30', 'Aerobic', 'Studio'], ['18:00', 'Cầu lông', 'Sân trong nhà'], ['19:30', 'Bóng rổ', 'Sân vận động']] },
  { day: 'Thứ 6', slots: [['06:00', 'Yoga buổi sáng', 'Studio'], ['17:30', 'Gym toàn thân', 'Phòng Gym'], ['19:00', 'Bơi kỹ thuật', 'Hồ bơi']] },
  { day: 'Thứ 7', slots: [['08:00', 'Aerobic', 'Studio'], ['09:30', 'Bóng rổ trẻ', 'Sân vận động'], ['15:00', 'Bơi tự do', 'Hồ bơi']] },
  { day: 'Chủ nhật', slots: [['08:00', 'Yoga thư giãn', 'Studio'], ['10:00', 'Gym tự do', 'Phòng Gym']] },
]
