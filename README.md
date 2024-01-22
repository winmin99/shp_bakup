# ShpBackup v3

### CHANGELOG 1.3.2
Release Date: 2020-04-24
- table.properties 보완 (하수 시설 추가)
- PostgreSQL 테이블 컬럼명 소문자 사용

### CHANGELOG 1.3.1
Release Date: 2020-01-31
- .properties 파일의 인코딩 UTF-8로 변경
- jar 빌드 파일명 GSS_ConvertSHP 로 변경

### CHANGELOG 1.2.1
Release Date: 2019-09-11
- table.properties: 역지변 추가
- '상수맨홀'의 'LAYER' 컬럼 검사 삭제
- config.properties git 에 추가

### CHANGELOG 1.2.0
Release Date: 2019-09-05
- MySql 데이터를 가지지 않고(=MySql 접속 없음) shp 에서 geom 정보만을 읽어 삽입하는 클래스 추가(상수관말, 편락관 등)
- 현재 대상이 되는 테이블명은 하드코딩되어 있음
##### 로그 기록 방식 변경
- error_log.log: 기존 로그와 동일하게 에러 기록
- history_log.log: 각 shp 파일별 작업 결과를 기록: 정상, 에러 건수 표시

### CHANGELOG 1.1.0
Release Date: 2019-08-21
-	config.properties: 백업 전/후로 PRE/POST Query 사용 여부 설정 가능(prequery.txt, postquery.txt)
-	table.properties: 테이블명 설정 가능. 값이 없으면 백업에서 제외됨
-	모든 설정 파일은 파일명 변경 금지
-	DBCP (Database Connection Pool) 사용: 설정값 추가 연구 필요
-	백업 대상 shp 파일 선택 기준(A부터 우선순위):
-	shp 확장자 파일 있음
-	table.properties 에 테이블명 있음
-	shp 파일크기가 100 바이트 이상: 개선 필요
-	config.properties 에서 설정한 shp 폴더 안에 5번 기준으로 선택된 파일이 없으면 백업 진행 X, 로그에 ‘백업 대상 shp 파일이 없음’ 메시지 기록됨
-	shp 파일별로 쓰레드 작업 실행: 서버 테이블에 INSERT 순서가 랜덤
-	테이블별로 백업 시작하기 전 테이블 비우기(TRUNCATE)를 1회 실행. 단, MySQL DB에서 데이터를 하나도 가져오지 못할 경우 비우기 작업을 롤백한다. 정상적인 데이터가 한 개라도 존재할 경우 백업이 진행되며, 이때 비워진 테이블은 롤백되지 않는다.
-	여러 에러 케이스에 대해 에러 로그 세분화: 파일명+테이블명+FTR_IDN+에러메시지 표시
-	데이터 INSERT 중 오류가 발생할 경우 해당 데이터만 백업을 건너뛰며, 정상 데이터는 정상 백업된다.
##### 결과 출력창
-	정상 완료: 해당 shp 파일에 대한 모든 백업이 정상적으로 완료
-	일부 에러: 해당 shp 파일 데이터 일부에서 오류 발생, 정상 데이터는 정상 백업. 로그 분석 필요
-	전체 에러: DB 문제 등으로 데이터를 하나도 가져오지 못한 경우 백업 시작 X, 롤백 실행됨
-	로그 파일: log 폴더내 위치, logback.log 파일에 기록되며, 누적된 로그 파일 크기가 5 MB 를 넘어가면 zip 으로 압축되고 새 로그 파일에서 이어서 기록
