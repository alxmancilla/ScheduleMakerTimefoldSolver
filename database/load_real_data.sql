-- ============================================================================
-- School Scheduling System - Demo Data Loading Script
-- ============================================================================
-- This script loads the same demo data used in DemoDataGenerator.java
-- Corresponds to the data generated in:
-- - generateTeachers()
-- - generateCourses()
-- - generateRooms()
-- - generateTimeslots()
-- - generateGroups()
-- - generateCourseAssignments()
-- ============================================================================


-- Set client encoding to UTF-8 for proper handling of special characters
SET client_encoding = 'UTF8';

-- Clear existing data (in correct order due to foreign keys)
DELETE FROM course_assignment;
DELETE FROM group_course;
DELETE FROM teacher_qualification;
DELETE FROM teacher_availability;
DELETE FROM student_group;
DELETE FROM timeslot;
DELETE FROM room;
DELETE FROM course;
DELETE FROM teacher;


INSERT INTO room ("name",building,"type",created_at,updated_at) VALUES
	 ('SALON 1','EDIFICIO 1','estándar',NOW(),NOW()),
	 ('SALON 2','EDIFICIO 1','estándar',NOW(),NOW()),
	 ('SALON 3','EDIFICIO 1','estándar',NOW(),NOW()),
	 ('SALON 4','EDIFICIO 1','taller',NOW(),NOW()),
	 ('SALON 5','EDIFICIO 1','estándar',NOW(),NOW()),
	 ('SALON 6','EDIFICIO 1','estándar',NOW(),NOW()),
	 ('SALON 7','EDIFICIO 2','estándar',NOW(),NOW()),
	 ('SALON 8','EDIFICIO 2','estándar',NOW(),NOW()),
	 ('SALON 9','EDIFICIO 2','estándar',NOW(),NOW()),
	 ('SALON 10','EDIFICIO 2','estándar',NOW(),NOW());
INSERT INTO room ("name",building,"type",created_at,updated_at) VALUES
	 ('SALON 11','EDIFICIO 2','estándar',NOW(),NOW()),
	 ('SALON 12','EDIFICIO 2','estándar',NOW(),NOW()),
	 ('SALON 13','EDIFICIO 3','estándar',NOW(),NOW()),
	 ('SALON 14','EDIFICIO 3','estándar',NOW(),NOW()),
	 ('SALON 15','EDIFICIO 3','estándar',NOW(),NOW()),
	 ('SALON 16','EDIFICIO 3','estándar',NOW(),NOW()),
	 ('SALON 17','EDIFICIO 3','estándar',NOW(),NOW()),
	 ('SALON 18','EDIFICIO 3','estándar',NOW(),NOW()),
	 ('SALON 19','EDIFICIO 3','estándar',NOW(),NOW()),
	 ('SALON 20','EDIFICIO 3','estándar',NOW(),NOW());
INSERT INTO room ("name",building,"type",created_at,updated_at) VALUES
	 ('SALON 21','EDIFICIO 3','estándar',NOW(),NOW()),
	 ('SALON 22','EDIFICIO 3','estándar',NOW(),NOW()),
	 ('SALON 23','EDIFICIO 3','estándar',NOW(),NOW()),
	 ('TEM 1','TALLER ELECTROMECANICA','taller electromecánica',NOW(),NOW()),
	 ('TEM 2','TALLER ELECTROMECANICA','taller electromecánica',NOW(),NOW()),
	 ('TEM 3','TALLER ELECTROMECANICA','taller electromecánica',NOW(),NOW()),
	 ('TE 1','TALLER ELECTRONICA','taller electrónica',NOW(),NOW()),
	 ('CC 1','CENTRO DE COMPUTO','centro de cómputo',NOW(),NOW()),
	 ('CC 2','CENTRO DE COMPUTO','centro de cómputo',NOW(),NOW()),
	 ('CC 3','CENTRO DE COMPUTO','centro de cómputo',NOW(),NOW()),
	 ('LQ 1','EDIFICIO 2','laboratorio',NOW(),NOW());

commit;

INSERT INTO timeslot (id,day_of_week,"hour",display_name,created_at) VALUES
	 ('1',1,7,'Lun 7-8',NOW()),
	 ('2',1,8,'Lun 8-9',NOW()),
	 ('3',1,9,'Lun 9-10',NOW()),
	 ('4',1,10,'Lun 10-11',NOW()),
	 ('5',1,11,'Lun 11-12',NOW()),
	 ('6',1,12,'Lun 12-13',NOW()),
	 ('7',1,13,'Lun 13-14',NOW()),
	 ('8',1,14,'Lun 14-15',NOW()),
	 ('9',2,7,'Mar 7-8',NOW()),
	 ('10',2,8,'Mar 8-9',NOW());
INSERT INTO timeslot (id,day_of_week,"hour",display_name,created_at) VALUES
	 ('11',2,9,'Mar 9-10',NOW()),
	 ('12',2,10,'Mar 10-11',NOW()),
	 ('13',2,11,'Mar 11-12',NOW()),
	 ('14',2,12,'Mar 12-13',NOW()),
	 ('15',2,13,'Mar 13-14',NOW()),
	 ('16',2,14,'Mar 14-15',NOW()),
	 ('17',3,7,'Mie 7-8',NOW()),
	 ('18',3,8,'Mie 8-9',NOW()),
	 ('19',3,9,'Mie 9-10',NOW()),
	 ('20',3,10,'Mie 10-11',NOW());
INSERT INTO timeslot (id,day_of_week,"hour",display_name,created_at) VALUES
	 ('21',3,11,'Mie 11-12',NOW()),
	 ('22',3,12,'Mie 12-13',NOW()),
	 ('23',3,13,'Mie 13-14',NOW()),
	 ('24',3,14,'Mie 14-15',NOW()),
	 ('25',4,7,'Jue 7-8',NOW()),
	 ('26',4,8,'Jue 8-9',NOW()),
	 ('27',4,9,'Jue 9-10',NOW()),
	 ('28',4,10,'Jue 10-11',NOW()),
	 ('29',4,11,'Jue 11-12',NOW()),
	 ('30',4,12,'Jue 12-13',NOW());
INSERT INTO timeslot (id,day_of_week,"hour",display_name,created_at) VALUES
	 ('31',4,13,'Jue 13-14',NOW()),
	 ('32',4,14,'Jue 14-15',NOW()),
	 ('33',5,7,'Vie 7-8',NOW()),
	 ('34',5,8,'Vie 8-9',NOW()),
	 ('35',5,9,'Vie 9-10',NOW()),
	 ('36',5,10,'Vie 10-11',NOW()),
	 ('37',5,11,'Vie 11-12',NOW()),
	 ('38',5,12,'Vie 12-13',NOW()),
	 ('39',5,13,'Vie 13-14',NOW()),
	 ('40',5,14,'Vie 14-15',NOW());


INSERT INTO course (id,"name",abbreviation,room_requirement,required_hours_per_week,semester,component,created_at,updated_at,active) VALUES
	 ('1','PENSAMIENTO MATEMATICO II','PENSAM. MATEM. II','estándar',4,'II','BASICAS',NOW(),NOW(),true),
	 ('2','INGLES II','INGLES  II','estándar',3,'II','BASICAS',NOW(),NOW(),true),
	 ('3','CIENCIAS NATURALES, EXPERIMENTALES Y TECNOLOGIA II. EL PODER DE LA ENERGIA.','CIEN NAT II','estándar',4,'II','BASICAS',NOW(),NOW(),true),
	 ('4','LENGUA Y COMUNICACION II','LENGUA Y COM II','estándar',3,'II','BASICAS',NOW(),NOW(),true),
	 ('5','CIENCIAS SOCIALES II','CIEN SOC II','estándar',2,'II','BASICAS',NOW(),NOW(),true),
	 ('6','CULTURA DIGITAL II','CUL DIG II','estándar',2,'II','BASICAS',NOW(),NOW(),true),
	 ('7','RECURSO SOCIOEMOCIONAL II','REC SOC II','estándar',1,'II','BASICAS',NOW(),NOW(),true),
	 ('8','EJECUTA PROCEDIMIENTOS ADMINISTRATIVOS DEL AREA DE RECURSOS HUMANOS','EJEC. PROC. ADM. A.R.H.','estándar',10,'II','TADRH',NOW(),NOW(),true),
	 ('9','GESTIONA DOCUMENTACION DEL AREA DE RECURSOS HUMANOS','GEST. DOC. A.R.H.','estándar',7,'II','TADRH',NOW(),NOW(),true),
	 ('10','DISEÑA PLANOS Y DIAGRAMAS ELECTRICOS Y ELECTRONICOS DE SISTEMAS ELECTROMECANICOS','DIS. PLA. DIAG. ELEC.SIST. ELEC.','taller electromecánica',4,'II','TEM',NOW(),NOW(),true);
INSERT INTO course (id,"name",abbreviation,room_requirement,required_hours_per_week,semester,component,created_at,updated_at,active) VALUES
	 ('11','REALIZA INSTALACIONES ELECTRICAS EN EQUIPOS ELECTROMECANICOS','REAL. INST. ELEC. EQU. ELEC.','taller electromecánica',9,'II','TEM',NOW(),NOW(),true),
	 ('12','REALIZA INSTALACIONES DE CIRCUITOS ELECTRONICOS EN SISTEMAS ELECTROMECANICOS','REAL. INST. CIR. ELEC. SIST. ELEC.','taller electromecánica',4,'II','TEM',NOW(),NOW(),true),
	 ('13','AUXILIA EN PROCEDIMIENTOS ADMINISTRATIVOS Y NORMATIVOS PARA IMPORTACIONES Y EXPORTACIONES DE MERCANCIAS','AUX. PROC.ADM. Y NOR. PARA IMPO. Y EXPOR. DE MERC.','estándar',10,'II','TCIA',NOW(),NOW(),true),
	 ('14','VERIFICA LA DOCUMENTACION PARA LA IMPORTACION Y EXPORTACION DE MERCANCIAS','VER. LA DOC. PARA LA IMPOR. Y EXPORTACIÓN DE MERC.','estándar',7,'II','TCIA',NOW(),NOW(),true),
	 ('15','REALIZA ANALISIS FISICOS Y QUIMICOS A LA MATERIA PRIMA','REA. ANA. FÍS. QUI. MAT. PRIMA','estándar',8,'II','TPIAL',NOW(),NOW(),true),
	 ('16','REALIZA ANALISIS MICROBIOLOGICOS A LA MATERIA PRIMA','REA. ANA. MICRO. MAT. PRIMA','taller',9,'II','TPIAL',NOW(),NOW(),true),
	 ('17','DISEÑA ALGORITMOS DE PROBLEMAS DE SEGURIDAD','DIS. ALGO.DE PROB. DE SEG.','estándar',6,'II','TCS',NOW(),NOW(),true),
	 ('18','IMPLEMENTA SCRIPTS EN UN LENGUAJE DE PROGRAMACION PARA SOL DE PROB DE SEGURIDAD','IMPLE. SCRI. EN LENG. DE PROG. PARA LA SOL. DE PROB. DE SEG.','centro de cómputo',11,'II','TCS',NOW(),NOW(),true),
	 ('19','DISEÑA SOFTWARE DE SISTEMAS INFORMATICOS','DIS. SOF. SIST. INFO.','centro de cómputo',5,'II','TPROG',NOW(),NOW(),true),
	 ('20','CODIFICA SOFTWARE DE SISTEMAS INFORMATICOS','COD. SOF. SIST. INFO.','centro de cómputo',7,'II','TPROG',NOW(),NOW(),true);
INSERT INTO course (id,"name",abbreviation,room_requirement,required_hours_per_week,semester,component,created_at,updated_at,active) VALUES
	 ('21','IMPLEMENTA SOFTWARE DE SISTEMAS INFORMATICOS','IMP. SOF. SIST. INFO.','centro de cómputo',5,'II','TPROG',NOW(),NOW(),true),
	 ('22','DESARROLLA ALGORITMOS PARA SOLUCIONAR PROBLEMAS','DES. ALG. PARA  SOLU. PROB.','centro de cómputo',9,'II','TIA',NOW(),NOW(),true),
	 ('23','ELABORA PROYECTOS CON PROGRAMACION LOGICA','ELAB. PROY. CON PROG. LOGICA','centro de cómputo',8,'II','TIA',NOW(),NOW(),true),
	 ('24','TEMAS SELECTOS DE MATEMATICAS I','TEM. SEL. MAT. I','estándar',4,'IV','BASICAS',NOW(),NOW(),true),
	 ('25','INGLES IV','INGLES IV','estándar',3,'IV','BASICAS',NOW(),NOW(),true),
	 ('26','CONCIENCIA HISTORICA I','CON. HIST. I','estándar',3,'IV','BASICAS',NOW(),NOW(),true),
	 ('27','REACCIONES QUIMICAS: CONSERVACION DE LA MATERIA EN LA FORMACION DE NUEVAS SUSTANCIAS','REACCIONES QUÍMICAS','estándar',4,'IV','BASICAS',NOW(),NOW(),true),
	 ('28','CIENCIAS SOCIALES III','CIEN. SOC. III','estándar',2,'IV','BASICAS',NOW(),NOW(),true),
	 ('29','RECURSOS SOCIOEMOCIONALES III','REC. SOCIOEMO. III','estándar',1,'IV','BASICAS',NOW(),NOW(),true),
	 ('30','TUTORIAS IV','TUTORIAS IV','estándar',1,'IV','BASICAS',NOW(),NOW(),true);
INSERT INTO course (id,"name",abbreviation,room_requirement,required_hours_per_week,semester,component,created_at,updated_at,active) VALUES
	 ('31','GESTIONA LOS PROCESOS DE CAPACITACION PARA EL DESARROLLO DEL TALENTO HUMANO','GES. PROC. CAP. DES. T.H.','estándar',10,'IV','TADRH',NOW(),NOW(),true),
	 ('32','PROMUEVE CONDICIONES DE TRABAJO SALUDABLES EN LA ORGANIZACION','PROM. CON. TRAB. SAL. ORG.','estándar',7,'IV','TADRH',NOW(),NOW(),true),
	 ('33','REALIZA ANALISIS FISICOS, QUIMICOS Y MICROBIOLOGICOS EN CARNES Y SUS DERIVADOS','REAL. ANALISIS FÍS. QUÍ. MICRO.','taller',6,'IV','TPIAL',NOW(),NOW(),true),
	 ('34','TRANSFORMA CARNE Y SUS DERIVADOS EN PRODUCTOS ALIMENTICIOS','REAL. PROC. TRANS. CARNICOS','taller',11,'IV','TPIAL',NOW(),NOW(),true),
	 ('35','MAQUINA PIEZAS MECANICAS EN TORNO Y FRESADORA CONVENCIONAL','MAQ. PZAS MEC. TOR FRES CONV.','taller electromecánica',6,'IV','TEM',NOW(),NOW(),true),
	 ('36','MAQUINA PIEZAS MECANICAS EN TORNO Y FRESADORA CNC','MAQ. PZAS MEC. TOR FRES CNC.','taller electromecánica',6,'IV','TEM',NOW(),NOW(),true),
	 ('37','CONSTRUYE ESTRUCTURAS METALICAS PARA LA INDUSTRIA','CON. EST. MET. INDUS.','taller electromecánica',5,'IV','TEM',NOW(),NOW(),true),
	 ('38','REALIZA MANTENIMIENTO A SISTEMAS ELECTRICOS DE POTENCIA','RLZA. MANTO. A SIST. ELEC. DE POTENCIA','taller electrónica',7,'IV','TEC',NOW(),NOW(),true),
	 ('39','PROGRAMA PLC PARA SISTEMAS AUTOMATIZADOS','PROG. PLC PARA SIS. AUTO.','taller electrónica',10,'IV','TEC',NOW(),NOW(),true),
	 ('40','IMPLEMENTA BASE DE DATOS RELACIONALES EN UN SISTEMA DE INFORMACION','IMP. BAS. DATOS REL. SIST. INF.','centro de cómputo',9,'IV','TPROG',NOW(),NOW(),true);
INSERT INTO course (id,"name",abbreviation,room_requirement,required_hours_per_week,semester,component,created_at,updated_at,active) VALUES
	 ('41','IMPLEMENTA BASE DE DATOS NO RELACIONALES EN UN SISTEMA DE INFORMACION','IMP. BAS. DATOS NO REL. SIST. INF.','centro de cómputo',8,'IV','TPROG',NOW(),NOW(),true),
	 ('42','DETECTA VULNERABILIDADES EN SISTEMAS INFORMATICOS','DET. VULN. SIST. INFO.','centro de cómputo',10,'IV','TCS',NOW(),NOW(),true),
	 ('43','CORRIGE VULNERABILIDADES EN SISTEMAS INFORMATICOS','CORRIGE VULN. SIST. INFO.','centro de cómputo',8,'IV','TCS',NOW(),NOW(),true),
	 ('44','TEMAS SELECTOS DE MATEMATICAS III','TEMAS SELC. DE MAT.','estándar',5,'VI','BASICAS',NOW(),NOW(),true),
	 ('45','CONCIENCIA HISTÓRICA. LA REALIDAD ACTUAL EN PERSPECTIVA HISTORICA.','CONC. HIST. REAL ACT. PERSP HIST.','estándar',3,'VI','BASICAS',NOW(),NOW(),true),
	 ('46','HUMANIDADES III','HUM. III','estándar',5,'VI','BASICAS',NOW(),NOW(),true),
	 ('47','ORGANISMOS: ESTRUCTURAS Y PROCESOS. HERENCIA Y EVOLUCIÓN BIOLÓGICA','ORG. ESTRC. Y PROC. HERENCIA Y EVO. BIO.','estándar',4,'VI','BASICAS',NOW(),NOW(),true),
	 ('48','HUMANISMO Y PENSAMIENTO FILOSÓFICO EN MÉXICO','HUM Y PENS. FILOS. EN MEX.','estándar',3,'VI','BASICAS',NOW(),NOW(),true),
	 ('49','INTERACCIONES HUMANAS CON LA NATURALEZA','INTE. HUMA. CON LA NAT.','estándar',3,'VI','BASICAS',NOW(),NOW(),true),
	 ('50','TUTORIAS III','TUTORIAS III','estándar',1,'VI','BASICAS',NOW(),NOW(),true);
INSERT INTO course (id,"name",abbreviation,room_requirement,required_hours_per_week,semester,component,created_at,updated_at,active) VALUES
	 ('51','AUXILIA EN EL CÁLCULO DE LA NOMINA ORDINARIA','AUX. EN EL CAL. DE LA NOM. ORD.','estándar',8,'VI','TADRH',NOW(),NOW(),true),
	 ('52','AUXILIA EN EL CÁLCULO DE LA NOMINA EXTRAORDINARIA','AUX. EN EL CAL. DE LA NOM. EXT.','estándar',4,'VI','TADRH',NOW(),NOW(),true),
	 ('53','REALIZA LOS ANÁLISIS FÍSICOS, QUÍMICOS Y MICROBIOLÓGICOS DE LOS PRODUCTOS DE CEREALES U OLEAGINOSAS Y PRODUCTOS DERIVADOS','REAL. ANAL FIS. QUIM. Y MICRO. DELOS PROD. DE CER. Y OLEAG. Y PROD. DERIV.','taller',5,'VI','TPIAL',NOW(),NOW(),true),
	 ('54','REALIZA LOS PROCESOS DE TRANSFORMACIÓN DE CEREALES Y PRODUCTOS DERIVADOS','REAL. PROC. DE TRANS. DE CER. Y PRO. DERV.','taller',7,'VI','TPIAL',NOW(),NOW(),true),
	 ('55','MANTIENE EQUIPOS HIDRAULICOS','MANT. EQUI. HIDR. ','taller electromecánica',4,'VI','TEM',NOW(),NOW(),true),
	 ('56','MANTIENE EQUIPOS NEUMATICOS','MANT. EQUI. NEUMATICOS','taller electromecánica',4,'VI','TEM',NOW(),NOW(),true),
	 ('57','MANTIENE EQUIPOS DE REFRIGERACION','MANT. EQUI. REFRIG.','taller electromecánica',4,'VI','TEM',NOW(),NOW(),true),
	 ('58','INSTALA SISTEMAS ELECTRONICOS DOMOTICOS','MANT. SIST. SEG. EDIFICIOS','taller electrónica',6,'VI','TEC',NOW(),NOW(),true),
	 ('59','INSTALA SISTEMAS ELECTRONICOS INDUSTRIALES AUTOMATIZADOS','IMP. SIST. AUTOMATIZADOS','taller electrónica',6,'VI','TEC',NOW(),NOW(),true),
	 ('60','DISEÑA APLICACIONES MOVILES MULTIPLATAFORMA','DIS. APP. MOVILES MULT.','estándar',5,'VI','TPROG',NOW(),NOW(),true);
INSERT INTO course (id,"name",abbreviation,room_requirement,required_hours_per_week,semester,component,created_at,updated_at,active) VALUES
	 ('61','IMPLEMENTA APLICACIONES MOVILES MULTIPLATAFORMA','IMPLE. APP. MOVILES MULT.','estándar',7,'VI','TPROG',NOW(),NOW(),true);

commit;

INSERT INTO teacher (id,"name",last_name,max_hours_per_week,created_at,updated_at) VALUES
	 ('48JAABQ','JUAN ANTONIO','ACEVEDO BELLO',40,NOW(),NOW()),
	 ('48DLAOV','DIANA LIZZETE','ANTUNEZ ORTIZ',40,NOW(),NOW()),
	 ('48SLAMC','SUSANA LEONOR','ARIZA MOTA',40,NOW(),NOW()),
	 ('48JBWT','JOSE','BAHENA WENCES',40,NOW(),NOW()),
	 ('48ABCJ','ANDRES','BARRIOS CASARRUBIAS',40,NOW(),NOW()),
	 ('46ICJM','ISAI','CANTOR JIMON',20,NOW(),NOW()),
	 ('46IJCAI','IRVING JAIR','CASTILLO AVILA',20,NOW(),NOW()),
	 ('48JACAJ','JOAQUIN ANGEL','CASTREJON ANGLI',40,NOW(),NOW()),
	 ('48BCCS','BALBINA','CATALAN CARDEÑO',40,NOW(),NOW()),
	 ('46FNDZG','FRANCISCO NARCES','DAVILA ZURITA',20,NOW(),NOW());
INSERT INTO teacher (id,"name",last_name,max_hours_per_week,created_at,updated_at) VALUES
	 ('47YAMSC','YAMMEL ANILU','MARTINEZ SAAVEDRA',30,NOW(),NOW()),
	 ('46REJA','RODRIGO','ESPINOSA JAVIER',20,NOW(),NOW()),
	 ('48LFGD','LETICIA','FLORES GARCIA',40,NOW(),NOW()),
	 ('47CGHV','CESAR','GARCIA HERNANDEZ',30,NOW(),NOW()),
	 ('48HGRO','HUGO','GARCIA RAMIREZ',40,NOW(),NOW()),
	 ('48MAGCE','MIGUEL ANGEL','GUZMAN CONTRERAS',40,NOW(),NOW()),
	 ('48YHGN','YASIR','HERRERA GARCIA',40,NOW(),NOW()),
	 ('48AJAP','ANTONIO','JIMENEZ ALTAMIRANO',40,NOW(),NOW()),
	 ('48DRLSO','DIANA ROSARIO','LLUCK SOBERANIS',40,NOW(),NOW()),
	 ('48GMMM','GUSTAVO','MELO MARTINEZ',40,NOW(),NOW());
INSERT INTO teacher (id,"name",last_name,max_hours_per_week,created_at,updated_at) VALUES
	 ('47LMMB','LORENA','MORALES MUÑIZ',30,NOW(),NOW()),
	 ('46LDNRS','LUCIA DANIELA','NUÑEZ RESENDIZ',20,NOW(),NOW()),
	 ('46JCRCY','JOSE CARLOS','RETANA CHAPARRO',20,NOW(),NOW()),
	 ('47FRSO','FRANCISCA','REYES SALAZAR',30,NOW(),NOW()),
	 ('48RRGB','ROGELIO','RIOS GALLARDO',40,NOW(),NOW()),
	 ('48ERNE','ENRIQUE','ROJAS NANDY',40,NOW(),NOW()),
	 ('48PBRAU','PABLO BENITO','ROSETE ALEMAN',40,NOW(),NOW()),
	 ('48ASOG','ALFREDO','SALAS OCAMPO',40,NOW(),NOW()),
	 ('48ASMO','ANTONIO','SALGADO MORGAN',40,NOW(),NOW()),
	 ('45EUSAS','EDGAR URIEL','SALMERON ASCENCIO',45,NOW(),NOW());
INSERT INTO teacher (id,"name",last_name,max_hours_per_week,created_at,updated_at) VALUES
	 ('48LSCS','LUIS','SANCHEZ CASTAÑEDA',40,NOW(),NOW()),
	 ('48ISOT','ISRAEL','SANTANA OLIVARES',40,NOW(),NOW()),
	 ('45LDLSRP','LETICIA','DE LOS SANTOS REYES',45,NOW(),NOW()),
	 ('48ASOE','ALFREDO','SOLIS OLEA',40,NOW(),NOW()),
	 ('46MASOC','MARCO ANTONIO','SOLIS OLEA',20,NOW(),NOW()),
	 ('48YESMR','YARA ESTHER','SUCRE MENDEZ',40,NOW(),NOW()),
	 ('46IUAW','ITZEL','URIBE ARIZMENDI',20,NOW(),NOW()),
	 ('47SVPE','SANTIAGO','VAZQUEZ PRESTEGUI',30,NOW(),NOW()),
	 ('46YVVGG','YAJAIRA VIRIDIANA','VELELA GONZALEZ',20,NOW(),NOW()),
	 ('45MVVT','MARIO','VERDIGUEL VELEZ',45,NOW(),NOW());
INSERT INTO teacher (id,"name",last_name,max_hours_per_week,created_at,updated_at) VALUES
	 ('46IZMO','IVAN','ZACAPALA MAGNO',20,NOW(),NOW());

commit;

-- MARIO VERDIGUEL VELEZ availability: Mon-Fri 7-14
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT '45MVVT', d, h
FROM generate_series(1, 5) AS d  -- Mon-Fri
CROSS JOIN generate_series(7, 14) AS h;

-- EDGAR URIEL SALMERON ASCENCIO availability: Mon-Fri 7-
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT '45EUSAS', d, h
FROM generate_series(1, 5) AS d  -- Mon-Fri
CROSS JOIN generate_series(7, 14) AS h;

-- LETICIA DE LOS SANTOS REYES availability: Mon-Fri 7-14
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT '45LDLSRP', d, h
FROM generate_series(1, 5) AS d  -- Mon-Fri
CROSS JOIN generate_series(7, 14) AS h;



INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48JAABQ',1,7,NOW()),
	 ('48JAABQ',1,8,NOW()),
	 ('48JAABQ',1,9,NOW()),
	 ('48JAABQ',1,10,NOW()),
	 ('48JAABQ',1,11,NOW()),
	 ('48JAABQ',1,12,NOW()),
	 ('48JAABQ',1,13,NOW()),
	 ('48JAABQ',1,14,NOW()),
	 ('48JAABQ',2,7,NOW()),
	 ('48JAABQ',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48JAABQ',2,9,NOW()),
	 ('48JAABQ',2,10,NOW()),
	 ('48JAABQ',2,11,NOW()),
	 ('48JAABQ',2,12,NOW()),
	 ('48JAABQ',2,13,NOW()),
	 ('48JAABQ',2,14,NOW()),
	 ('48JAABQ',3,7,NOW()),
	 ('48JAABQ',3,8,NOW()),
	 ('48JAABQ',3,9,NOW()),
	 ('48JAABQ',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48JAABQ',3,11,NOW()),
	 ('48JAABQ',3,12,NOW()),
	 ('48JAABQ',3,13,NOW()),
	 ('48JAABQ',3,14,NOW()),
	 ('48JAABQ',4,7,NOW()),
	 ('48JAABQ',4,8,NOW()),
	 ('48JAABQ',4,9,NOW()),
	 ('48JAABQ',4,10,NOW()),
	 ('48JAABQ',4,11,NOW()),
	 ('48JAABQ',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48JAABQ',4,13,NOW()),
	 ('48JAABQ',4,14,NOW()),
	 ('48JAABQ',5,7,NOW()),
	 ('48JAABQ',5,8,NOW()),
	 ('48JAABQ',5,9,NOW()),
	 ('48JAABQ',5,10,NOW()),
	 ('48JAABQ',5,11,NOW()),
	 ('48JAABQ',5,12,NOW()),
	 ('48JAABQ',5,13,NOW()),
	 ('48JAABQ',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48DLAOV',1,7,NOW()),
	 ('48DLAOV',1,8,NOW()),
	 ('48DLAOV',1,9,NOW()),
	 ('48DLAOV',1,10,NOW()),
	 ('48DLAOV',1,11,NOW()),
	 ('48DLAOV',1,12,NOW()),
	 ('48DLAOV',1,13,NOW()),
	 ('48DLAOV',1,14,NOW()),
	 ('48DLAOV',2,7,NOW()),
	 ('48DLAOV',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48DLAOV',2,9,NOW()),
	 ('48DLAOV',2,10,NOW()),
	 ('48DLAOV',2,11,NOW()),
	 ('48DLAOV',2,12,NOW()),
	 ('48DLAOV',2,13,NOW()),
	 ('48DLAOV',2,14,NOW()),
	 ('48DLAOV',3,7,NOW()),
	 ('48DLAOV',3,8,NOW()),
	 ('48DLAOV',3,9,NOW()),
	 ('48DLAOV',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48DLAOV',3,11,NOW()),
	 ('48DLAOV',3,12,NOW()),
	 ('48DLAOV',3,13,NOW()),
	 ('48DLAOV',3,14,NOW()),
	 ('48DLAOV',4,7,NOW()),
	 ('48DLAOV',4,8,NOW()),
	 ('48DLAOV',4,9,NOW()),
	 ('48DLAOV',4,10,NOW()),
	 ('48DLAOV',4,11,NOW()),
	 ('48DLAOV',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48DLAOV',4,13,NOW()),
	 ('48DLAOV',4,14,NOW()),
	 ('48DLAOV',5,7,NOW()),
	 ('48DLAOV',5,8,NOW()),
	 ('48DLAOV',5,9,NOW()),
	 ('48DLAOV',5,10,NOW()),
	 ('48DLAOV',5,11,NOW()),
	 ('48DLAOV',5,12,NOW()),
	 ('48DLAOV',5,13,NOW()),
	 ('48DLAOV',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48SLAMC',1,7,NOW()),
	 ('48SLAMC',1,8,NOW()),
	 ('48SLAMC',1,9,NOW()),
	 ('48SLAMC',1,10,NOW()),
	 ('48SLAMC',1,11,NOW()),
	 ('48SLAMC',1,12,NOW()),
	 ('48SLAMC',1,13,NOW()),
	 ('48SLAMC',1,14,NOW()),
	 ('48SLAMC',2,7,NOW()),
	 ('48SLAMC',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48SLAMC',2,9,NOW()),
	 ('48SLAMC',2,10,NOW()),
	 ('48SLAMC',2,11,NOW()),
	 ('48SLAMC',2,12,NOW()),
	 ('48SLAMC',2,13,NOW()),
	 ('48SLAMC',2,14,NOW()),
	 ('48SLAMC',3,7,NOW()),
	 ('48SLAMC',3,8,NOW()),
	 ('48SLAMC',3,9,NOW()),
	 ('48SLAMC',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48SLAMC',3,11,NOW()),
	 ('48SLAMC',3,12,NOW()),
	 ('48SLAMC',3,13,NOW()),
	 ('48SLAMC',3,14,NOW()),
	 ('48SLAMC',4,7,NOW()),
	 ('48SLAMC',4,8,NOW()),
	 ('48SLAMC',4,9,NOW()),
	 ('48SLAMC',4,10,NOW()),
	 ('48SLAMC',4,11,NOW()),
	 ('48SLAMC',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48SLAMC',4,13,NOW()),
	 ('48SLAMC',4,14,NOW()),
	 ('48SLAMC',5,7,NOW()),
	 ('48SLAMC',5,8,NOW()),
	 ('48SLAMC',5,9,NOW()),
	 ('48SLAMC',5,10,NOW()),
	 ('48SLAMC',5,11,NOW()),
	 ('48SLAMC',5,12,NOW()),
	 ('48SLAMC',5,13,NOW()),
	 ('48SLAMC',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48JBWT',1,7,NOW()),
	 ('48JBWT',1,8,NOW()),
	 ('48JBWT',1,9,NOW()),
	 ('48JBWT',1,10,NOW()),
	 ('48JBWT',1,11,NOW()),
	 ('48JBWT',1,12,NOW()),
	 ('48JBWT',1,13,NOW()),
	 ('48JBWT',1,14,NOW()),
	 ('48JBWT',2,7,NOW()),
	 ('48JBWT',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48JBWT',2,9,NOW()),
	 ('48JBWT',2,10,NOW()),
	 ('48JBWT',2,11,NOW()),
	 ('48JBWT',2,12,NOW()),
	 ('48JBWT',2,13,NOW()),
	 ('48JBWT',2,14,NOW()),
	 ('48JBWT',3,7,NOW()),
	 ('48JBWT',3,8,NOW()),
	 ('48JBWT',3,9,NOW()),
	 ('48JBWT',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48JBWT',3,11,NOW()),
	 ('48JBWT',3,12,NOW()),
	 ('48JBWT',3,13,NOW()),
	 ('48JBWT',3,14,NOW()),
	 ('48JBWT',4,7,NOW()),
	 ('48JBWT',4,8,NOW()),
	 ('48JBWT',4,9,NOW()),
	 ('48JBWT',4,10,NOW()),
	 ('48JBWT',4,11,NOW()),
	 ('48JBWT',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48JBWT',4,13,NOW()),
	 ('48JBWT',4,14,NOW()),
	 ('48JBWT',5,7,NOW()),
	 ('48JBWT',5,8,NOW()),
	 ('48JBWT',5,9,NOW()),
	 ('48JBWT',5,10,NOW()),
	 ('48JBWT',5,11,NOW()),
	 ('48JBWT',5,12,NOW()),
	 ('48JBWT',5,13,NOW()),
	 ('48JBWT',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ABCJ',1,7,NOW()),
	 ('48ABCJ',1,8,NOW()),
	 ('48ABCJ',1,9,NOW()),
	 ('48ABCJ',1,10,NOW()),
	 ('48ABCJ',1,11,NOW()),
	 ('48ABCJ',1,12,NOW()),
	 ('48ABCJ',1,13,NOW()),
	 ('48ABCJ',1,14,NOW()),
	 ('48ABCJ',2,7,NOW()),
	 ('48ABCJ',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ABCJ',2,9,NOW()),
	 ('48ABCJ',2,10,NOW()),
	 ('48ABCJ',2,11,NOW()),
	 ('48ABCJ',2,12,NOW()),
	 ('48ABCJ',2,13,NOW()),
	 ('48ABCJ',2,14,NOW()),
	 ('48ABCJ',3,7,NOW()),
	 ('48ABCJ',3,8,NOW()),
	 ('48ABCJ',3,9,NOW()),
	 ('48ABCJ',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ABCJ',3,11,NOW()),
	 ('48ABCJ',3,12,NOW()),
	 ('48ABCJ',3,13,NOW()),
	 ('48ABCJ',3,14,NOW()),
	 ('48ABCJ',4,7,NOW()),
	 ('48ABCJ',4,8,NOW()),
	 ('48ABCJ',4,9,NOW()),
	 ('48ABCJ',4,10,NOW()),
	 ('48ABCJ',4,11,NOW()),
	 ('48ABCJ',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ABCJ',4,13,NOW()),
	 ('48ABCJ',4,14,NOW()),
	 ('48ABCJ',5,7,NOW()),
	 ('48ABCJ',5,8,NOW()),
	 ('48ABCJ',5,9,NOW()),
	 ('48ABCJ',5,10,NOW()),
	 ('48ABCJ',5,11,NOW()),
	 ('48ABCJ',5,12,NOW()),
	 ('48ABCJ',5,13,NOW()),
	 ('48ABCJ',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('46ICJM',1,7,NOW()),
	 ('46ICJM',1,8,NOW()),
	 ('46ICJM',1,9,NOW()),
	 ('46ICJM',1,10,NOW()),
	 ('46ICJM',2,7,NOW()),
	 ('46ICJM',2,8,NOW()),
	 ('46ICJM',2,9,NOW()),
	 ('46ICJM',2,10,NOW()),
	 ('46ICJM',3,7,NOW()),
	 ('46ICJM',3,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('46ICJM',3,9,NOW()),
	 ('46ICJM',3,10,NOW()),
	 ('46ICJM',4,7,NOW()),
	 ('46ICJM',4,8,NOW()),
	 ('46ICJM',4,9,NOW()),
	 ('46ICJM',4,10,NOW()),
	 ('46ICJM',5,7,NOW()),
	 ('46ICJM',5,8,NOW()),
	 ('46ICJM',5,9,NOW()),
	 ('46ICJM',5,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('46IJCAI',1,7,NOW()),
	 ('46IJCAI',1,8,NOW()),
	 ('46IJCAI',1,9,NOW()),
	 ('46IJCAI',1,10,NOW()),
	 ('46IJCAI',2,7,NOW()),
	 ('46IJCAI',2,8,NOW()),
	 ('46IJCAI',2,9,NOW()),
	 ('46IJCAI',2,10,NOW()),
	 ('46IJCAI',3,7,NOW()),
	 ('46IJCAI',3,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('46IJCAI',3,9,NOW()),
	 ('46IJCAI',3,10,NOW()),
	 ('46IJCAI',4,7,NOW()),
	 ('46IJCAI',4,8,NOW()),
	 ('46IJCAI',4,9,NOW()),
	 ('46IJCAI',4,10,NOW()),
	 ('46IJCAI',5,7,NOW()),
	 ('46IJCAI',5,8,NOW()),
	 ('46IJCAI',5,9,NOW()),
	 ('46IJCAI',5,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48JACAJ',1,7,NOW()),
	 ('48JACAJ',1,8,NOW()),
	 ('48JACAJ',1,9,NOW()),
	 ('48JACAJ',1,10,NOW()),
	 ('48JACAJ',1,11,NOW()),
	 ('48JACAJ',1,12,NOW()),
	 ('48JACAJ',1,13,NOW()),
	 ('48JACAJ',1,14,NOW()),
	 ('48JACAJ',2,7,NOW()),
	 ('48JACAJ',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48JACAJ',2,9,NOW()),
	 ('48JACAJ',2,10,NOW()),
	 ('48JACAJ',2,11,NOW()),
	 ('48JACAJ',2,12,NOW()),
	 ('48JACAJ',2,13,NOW()),
	 ('48JACAJ',2,14,NOW()),
	 ('48JACAJ',3,7,NOW()),
	 ('48JACAJ',3,8,NOW()),
	 ('48JACAJ',3,9,NOW()),
	 ('48JACAJ',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48JACAJ',3,11,NOW()),
	 ('48JACAJ',3,12,NOW()),
	 ('48JACAJ',3,13,NOW()),
	 ('48JACAJ',3,14,NOW()),
	 ('48JACAJ',4,7,NOW()),
	 ('48JACAJ',4,8,NOW()),
	 ('48JACAJ',4,9,NOW()),
	 ('48JACAJ',4,10,NOW()),
	 ('48JACAJ',4,11,NOW()),
	 ('48JACAJ',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48JACAJ',4,13,NOW()),
	 ('48JACAJ',4,14,NOW()),
	 ('48JACAJ',5,7,NOW()),
	 ('48JACAJ',5,8,NOW()),
	 ('48JACAJ',5,9,NOW()),
	 ('48JACAJ',5,10,NOW()),
	 ('48JACAJ',5,11,NOW()),
	 ('48JACAJ',5,12,NOW()),
	 ('48JACAJ',5,13,NOW()),
	 ('48JACAJ',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48BCCS',1,7,NOW()),
	 ('48BCCS',1,8,NOW()),
	 ('48BCCS',1,9,NOW()),
	 ('48BCCS',1,10,NOW()),
	 ('48BCCS',1,11,NOW()),
	 ('48BCCS',1,12,NOW()),
	 ('48BCCS',1,13,NOW()),
	 ('48BCCS',1,14,NOW()),
	 ('48BCCS',2,7,NOW()),
	 ('48BCCS',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48BCCS',2,9,NOW()),
	 ('48BCCS',2,10,NOW()),
	 ('48BCCS',2,11,NOW()),
	 ('48BCCS',2,12,NOW()),
	 ('48BCCS',2,13,NOW()),
	 ('48BCCS',2,14,NOW()),
	 ('48BCCS',3,7,NOW()),
	 ('48BCCS',3,8,NOW()),
	 ('48BCCS',3,9,NOW()),
	 ('48BCCS',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48BCCS',3,11,NOW()),
	 ('48BCCS',3,12,NOW()),
	 ('48BCCS',3,13,NOW()),
	 ('48BCCS',3,14,NOW()),
	 ('48BCCS',4,7,NOW()),
	 ('48BCCS',4,8,NOW()),
	 ('48BCCS',4,9,NOW()),
	 ('48BCCS',4,10,NOW()),
	 ('48BCCS',4,11,NOW()),
	 ('48BCCS',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48BCCS',4,13,NOW()),
	 ('48BCCS',4,14,NOW()),
	 ('48BCCS',5,7,NOW()),
	 ('48BCCS',5,8,NOW()),
	 ('48BCCS',5,9,NOW()),
	 ('48BCCS',5,10,NOW()),
	 ('48BCCS',5,11,NOW()),
	 ('48BCCS',5,12,NOW()),
	 ('48BCCS',5,13,NOW()),
	 ('48BCCS',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('46FNDZG',1,7,NOW()),
	 ('46FNDZG',1,8,NOW()),
	 ('46FNDZG',1,9,NOW()),
	 ('46FNDZG',1,10,NOW()),
	 ('46FNDZG',2,7,NOW()),
	 ('46FNDZG',2,8,NOW()),
	 ('46FNDZG',2,9,NOW()),
	 ('46FNDZG',2,10,NOW()),
	 ('46FNDZG',3,7,NOW()),
	 ('46FNDZG',3,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('46FNDZG',3,9,NOW()),
	 ('46FNDZG',3,10,NOW()),
	 ('46FNDZG',4,7,NOW()),
	 ('46FNDZG',4,8,NOW()),
	 ('46FNDZG',4,9,NOW()),
	 ('46FNDZG',4,10,NOW()),
	 ('46FNDZG',5,7,NOW()),
	 ('46FNDZG',5,8,NOW()),
	 ('46FNDZG',5,9,NOW()),
	 ('46FNDZG',5,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('47YAMSC',1,7,NOW()),
	 ('47YAMSC',1,8,NOW()),
	 ('47YAMSC',1,9,NOW()),
	 ('47YAMSC',1,10,NOW()),
	 ('47YAMSC',1,11,NOW()),
	 ('47YAMSC',1,12,NOW()),
	 ('47YAMSC',2,7,NOW()),
	 ('47YAMSC',2,8,NOW()),
	 ('47YAMSC',2,9,NOW()),
	 ('47YAMSC',2,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('47YAMSC',2,11,NOW()),
	 ('47YAMSC',2,12,NOW()),
	 ('47YAMSC',3,7,NOW()),
	 ('47YAMSC',3,8,NOW()),
	 ('47YAMSC',3,9,NOW()),
	 ('47YAMSC',3,10,NOW()),
	 ('47YAMSC',3,11,NOW()),
	 ('47YAMSC',3,12,NOW()),
	 ('47YAMSC',4,7,NOW()),
	 ('47YAMSC',4,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('47YAMSC',4,9,NOW()),
	 ('47YAMSC',4,10,NOW()),
	 ('47YAMSC',4,11,NOW()),
	 ('47YAMSC',4,12,NOW()),
	 ('47YAMSC',5,7,NOW()),
	 ('47YAMSC',5,8,NOW()),
	 ('47YAMSC',5,9,NOW()),
	 ('47YAMSC',5,10,NOW()),
	 ('47YAMSC',5,11,NOW()),
	 ('47YAMSC',5,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('46REJA',1,7,NOW()),
	 ('46REJA',1,8,NOW()),
	 ('46REJA',1,9,NOW()),
	 ('46REJA',1,10,NOW()),
	 ('46REJA',2,7,NOW()),
	 ('46REJA',2,8,NOW()),
	 ('46REJA',2,9,NOW()),
	 ('46REJA',2,10,NOW()),
	 ('46REJA',3,7,NOW()),
	 ('46REJA',3,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('46REJA',3,9,NOW()),
	 ('46REJA',3,10,NOW()),
	 ('46REJA',4,7,NOW()),
	 ('46REJA',4,8,NOW()),
	 ('46REJA',4,9,NOW()),
	 ('46REJA',4,10,NOW()),
	 ('46REJA',5,7,NOW()),
	 ('46REJA',5,8,NOW()),
	 ('46REJA',5,9,NOW()),
	 ('46REJA',5,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48LFGD',1,7,NOW()),
	 ('48LFGD',1,8,NOW()),
	 ('48LFGD',1,9,NOW()),
	 ('48LFGD',1,10,NOW()),
	 ('48LFGD',1,11,NOW()),
	 ('48LFGD',1,12,NOW()),
	 ('48LFGD',1,13,NOW()),
	 ('48LFGD',1,14,NOW()),
	 ('48LFGD',2,7,NOW()),
	 ('48LFGD',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48LFGD',2,9,NOW()),
	 ('48LFGD',2,10,NOW()),
	 ('48LFGD',2,11,NOW()),
	 ('48LFGD',2,12,NOW()),
	 ('48LFGD',2,13,NOW()),
	 ('48LFGD',2,14,NOW()),
	 ('48LFGD',3,7,NOW()),
	 ('48LFGD',3,8,NOW()),
	 ('48LFGD',3,9,NOW()),
	 ('48LFGD',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48LFGD',3,11,NOW()),
	 ('48LFGD',3,12,NOW()),
	 ('48LFGD',3,13,NOW()),
	 ('48LFGD',3,14,NOW()),
	 ('48LFGD',4,7,NOW()),
	 ('48LFGD',4,8,NOW()),
	 ('48LFGD',4,9,NOW()),
	 ('48LFGD',4,10,NOW()),
	 ('48LFGD',4,11,NOW()),
	 ('48LFGD',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48LFGD',4,13,NOW()),
	 ('48LFGD',4,14,NOW()),
	 ('48LFGD',5,7,NOW()),
	 ('48LFGD',5,8,NOW()),
	 ('48LFGD',5,9,NOW()),
	 ('48LFGD',5,10,NOW()),
	 ('48LFGD',5,11,NOW()),
	 ('48LFGD',5,12,NOW()),
	 ('48LFGD',5,13,NOW()),
	 ('48LFGD',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('47CGHV',1,7,NOW()),
	 ('47CGHV',1,8,NOW()),
	 ('47CGHV',1,9,NOW()),
	 ('47CGHV',1,10,NOW()),
	 ('47CGHV',1,11,NOW()),
	 ('47CGHV',1,12,NOW()),
	 ('47CGHV',2,7,NOW()),
	 ('47CGHV',2,8,NOW()),
	 ('47CGHV',2,9,NOW()),
	 ('47CGHV',2,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('47CGHV',2,11,NOW()),
	 ('47CGHV',2,12,NOW()),
	 ('47CGHV',3,7,NOW()),
	 ('47CGHV',3,8,NOW()),
	 ('47CGHV',3,9,NOW()),
	 ('47CGHV',3,10,NOW()),
	 ('47CGHV',3,11,NOW()),
	 ('47CGHV',3,12,NOW()),
	 ('47CGHV',4,7,NOW()),
	 ('47CGHV',4,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('47CGHV',4,9,NOW()),
	 ('47CGHV',4,10,NOW()),
	 ('47CGHV',4,11,NOW()),
	 ('47CGHV',4,12,NOW()),
	 ('47CGHV',5,7,NOW()),
	 ('47CGHV',5,8,NOW()),
	 ('47CGHV',5,9,NOW()),
	 ('47CGHV',5,10,NOW()),
	 ('47CGHV',5,11,NOW()),
	 ('47CGHV',5,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48HGRO',1,7,NOW()),
	 ('48HGRO',1,8,NOW()),
	 ('48HGRO',1,9,NOW()),
	 ('48HGRO',1,10,NOW()),
	 ('48HGRO',1,11,NOW()),
	 ('48HGRO',1,12,NOW()),
	 ('48HGRO',1,13,NOW()),
	 ('48HGRO',1,14,NOW()),
	 ('48HGRO',2,7,NOW()),
	 ('48HGRO',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48HGRO',2,9,NOW()),
	 ('48HGRO',2,10,NOW()),
	 ('48HGRO',2,11,NOW()),
	 ('48HGRO',2,12,NOW()),
	 ('48HGRO',2,13,NOW()),
	 ('48HGRO',2,14,NOW()),
	 ('48HGRO',3,7,NOW()),
	 ('48HGRO',3,8,NOW()),
	 ('48HGRO',3,9,NOW()),
	 ('48HGRO',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48HGRO',3,11,NOW()),
	 ('48HGRO',3,12,NOW()),
	 ('48HGRO',3,13,NOW()),
	 ('48HGRO',3,14,NOW()),
	 ('48HGRO',4,7,NOW()),
	 ('48HGRO',4,8,NOW()),
	 ('48HGRO',4,9,NOW()),
	 ('48HGRO',4,10,NOW()),
	 ('48HGRO',4,11,NOW()),
	 ('48HGRO',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48HGRO',4,13,NOW()),
	 ('48HGRO',4,14,NOW()),
	 ('48HGRO',5,7,NOW()),
	 ('48HGRO',5,8,NOW()),
	 ('48HGRO',5,9,NOW()),
	 ('48HGRO',5,10,NOW()),
	 ('48HGRO',5,11,NOW()),
	 ('48HGRO',5,12,NOW()),
	 ('48HGRO',5,13,NOW()),
	 ('48HGRO',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48MAGCE',1,7,NOW()),
	 ('48MAGCE',1,8,NOW()),
	 ('48MAGCE',1,9,NOW()),
	 ('48MAGCE',1,10,NOW()),
	 ('48MAGCE',1,11,NOW()),
	 ('48MAGCE',1,12,NOW()),
	 ('48MAGCE',1,13,NOW()),
	 ('48MAGCE',1,14,NOW()),
	 ('48MAGCE',2,7,NOW()),
	 ('48MAGCE',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48MAGCE',2,9,NOW()),
	 ('48MAGCE',2,10,NOW()),
	 ('48MAGCE',2,11,NOW()),
	 ('48MAGCE',2,12,NOW()),
	 ('48MAGCE',2,13,NOW()),
	 ('48MAGCE',2,14,NOW()),
	 ('48MAGCE',3,7,NOW()),
	 ('48MAGCE',3,8,NOW()),
	 ('48MAGCE',3,9,NOW()),
	 ('48MAGCE',3,10,NOW()),
	 ('48MAGCE',3,11,NOW()),
	 ('48MAGCE',3,12,NOW()),
	 ('48MAGCE',3,13,NOW()),
	 ('48MAGCE',3,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48MAGCE',4,7,NOW()),
	 ('48MAGCE',4,8,NOW()),
	 ('48MAGCE',4,9,NOW()),
	 ('48MAGCE',4,10,NOW()),
	 ('48MAGCE',4,11,NOW()),
	 ('48MAGCE',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48MAGCE',4,13,NOW()),
	 ('48MAGCE',4,14,NOW()),
	 ('48MAGCE',5,7,NOW()),
	 ('48MAGCE',5,8,NOW()),
	 ('48MAGCE',5,9,NOW()),
	 ('48MAGCE',5,10,NOW()),
	 ('48MAGCE',5,11,NOW()),
	 ('48MAGCE',5,12,NOW()),
	 ('48MAGCE',5,13,NOW()),
	 ('48MAGCE',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48YHGN',1,7,NOW()),
	 ('48YHGN',1,8,NOW()),
	 ('48YHGN',1,9,NOW()),
	 ('48YHGN',1,10,NOW()),
	 ('48YHGN',1,11,NOW()),
	 ('48YHGN',1,12,NOW()),
	 ('48YHGN',1,13,NOW()),
	 ('48YHGN',1,14,NOW()),
	 ('48YHGN',2,7,NOW()),
	 ('48YHGN',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48YHGN',2,9,NOW()),
	 ('48YHGN',2,10,NOW()),
	 ('48YHGN',2,11,NOW()),
	 ('48YHGN',2,12,NOW()),
	 ('48YHGN',2,13,NOW()),
	 ('48YHGN',2,14,NOW()),
	 ('48YHGN',3,7,NOW()),
	 ('48YHGN',3,8,NOW()),
	 ('48YHGN',3,9,NOW()),
	 ('48YHGN',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48YHGN',3,11,NOW()),
	 ('48YHGN',3,12,NOW()),
	 ('48YHGN',3,13,NOW()),
	 ('48YHGN',3,14,NOW()),
	 ('48YHGN',4,7,NOW()),
	 ('48YHGN',4,8,NOW()),
	 ('48YHGN',4,9,NOW()),
	 ('48YHGN',4,10,NOW()),
	 ('48YHGN',4,11,NOW()),
	 ('48YHGN',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48YHGN',4,13,NOW()),
	 ('48YHGN',4,14,NOW()),
	 ('48YHGN',5,7,NOW()),
	 ('48YHGN',5,8,NOW()),
	 ('48YHGN',5,9,NOW()),
	 ('48YHGN',5,10,NOW()),
	 ('48YHGN',5,11,NOW()),
	 ('48YHGN',5,12,NOW()),
	 ('48YHGN',5,13,NOW()),
	 ('48YHGN',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48AJAP',1,7,NOW()),
	 ('48AJAP',1,8,NOW()),
	 ('48AJAP',1,9,NOW()),
	 ('48AJAP',1,10,NOW()),
	 ('48AJAP',1,11,NOW()),
	 ('48AJAP',1,12,NOW()),
	 ('48AJAP',1,13,NOW()),
	 ('48AJAP',1,14,NOW()),
	 ('48AJAP',2,7,NOW()),
	 ('48AJAP',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48AJAP',2,9,NOW()),
	 ('48AJAP',2,10,NOW()),
	 ('48AJAP',2,11,NOW()),
	 ('48AJAP',2,12,NOW()),
	 ('48AJAP',2,13,NOW()),
	 ('48AJAP',2,14,NOW()),
	 ('48AJAP',3,7,NOW()),
	 ('48AJAP',3,8,NOW()),
	 ('48AJAP',3,9,NOW()),
	 ('48AJAP',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48AJAP',3,11,NOW()),
	 ('48AJAP',3,12,NOW()),
	 ('48AJAP',3,13,NOW()),
	 ('48AJAP',3,14,NOW()),
	 ('48AJAP',4,7,NOW()),
	 ('48AJAP',4,8,NOW()),
	 ('48AJAP',4,9,NOW()),
	 ('48AJAP',4,10,NOW()),
	 ('48AJAP',4,11,NOW()),
	 ('48AJAP',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48AJAP',4,13,NOW()),
	 ('48AJAP',4,14,NOW()),
	 ('48AJAP',5,7,NOW()),
	 ('48AJAP',5,8,NOW()),
	 ('48AJAP',5,9,NOW()),
	 ('48AJAP',5,10,NOW()),
	 ('48AJAP',5,11,NOW()),
	 ('48AJAP',5,12,NOW()),
	 ('48AJAP',5,13,NOW()),
	 ('48AJAP',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48DRLSO',1,7,NOW()),
	 ('48DRLSO',1,8,NOW()),
	 ('48DRLSO',1,9,NOW()),
	 ('48DRLSO',1,10,NOW()),
	 ('48DRLSO',1,11,NOW()),
	 ('48DRLSO',1,12,NOW()),
	 ('48DRLSO',1,13,NOW()),
	 ('48DRLSO',1,14,NOW()),
	 ('48DRLSO',2,7,NOW()),
	 ('48DRLSO',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48DRLSO',2,9,NOW()),
	 ('48DRLSO',2,10,NOW()),
	 ('48DRLSO',2,11,NOW()),
	 ('48DRLSO',2,12,NOW()),
	 ('48DRLSO',2,13,NOW()),
	 ('48DRLSO',2,14,NOW()),
	 ('48DRLSO',3,7,NOW()),
	 ('48DRLSO',3,8,NOW()),
	 ('48DRLSO',3,9,NOW()),
	 ('48DRLSO',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48DRLSO',3,11,NOW()),
	 ('48DRLSO',3,12,NOW()),
	 ('48DRLSO',3,13,NOW()),
	 ('48DRLSO',3,14,NOW()),
	 ('48DRLSO',4,7,NOW()),
	 ('48DRLSO',4,8,NOW()),
	 ('48DRLSO',4,9,NOW()),
	 ('48DRLSO',4,10,NOW()),
	 ('48DRLSO',4,11,NOW()),
	 ('48DRLSO',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48DRLSO',4,13,NOW()),
	 ('48DRLSO',4,14,NOW()),
	 ('48DRLSO',5,7,NOW()),
	 ('48DRLSO',5,8,NOW()),
	 ('48DRLSO',5,9,NOW()),
	 ('48DRLSO',5,10,NOW()),
	 ('48DRLSO',5,11,NOW()),
	 ('48DRLSO',5,12,NOW()),
	 ('48DRLSO',5,13,NOW()),
	 ('48DRLSO',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48GMMM',1,7,NOW()),
	 ('48GMMM',1,8,NOW()),
	 ('48GMMM',1,9,NOW()),
	 ('48GMMM',1,10,NOW()),
	 ('48GMMM',1,11,NOW()),
	 ('48GMMM',1,12,NOW()),
	 ('48GMMM',1,13,NOW()),
	 ('48GMMM',1,14,NOW()),
	 ('48GMMM',2,7,NOW()),
	 ('48GMMM',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48GMMM',2,9,NOW()),
	 ('48GMMM',2,10,NOW()),
	 ('48GMMM',2,11,NOW()),
	 ('48GMMM',2,12,NOW()),
	 ('48GMMM',2,13,NOW()),
	 ('48GMMM',2,14,NOW()),
	 ('48GMMM',3,7,NOW()),
	 ('48GMMM',3,8,NOW()),
	 ('48GMMM',3,9,NOW()),
	 ('48GMMM',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48GMMM',3,11,NOW()),
	 ('48GMMM',3,12,NOW()),
	 ('48GMMM',3,13,NOW()),
	 ('48GMMM',3,14,NOW()),
	 ('48GMMM',4,7,NOW()),
	 ('48GMMM',4,8,NOW()),
	 ('48GMMM',4,9,NOW()),
	 ('48GMMM',4,10,NOW()),
	 ('48GMMM',4,11,NOW()),
	 ('48GMMM',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48GMMM',4,13,NOW()),
	 ('48GMMM',4,14,NOW()),
	 ('48GMMM',5,7,NOW()),
	 ('48GMMM',5,8,NOW()),
	 ('48GMMM',5,9,NOW()),
	 ('48GMMM',5,10,NOW()),
	 ('48GMMM',5,11,NOW()),
	 ('48GMMM',5,12,NOW()),
	 ('48GMMM',5,13,NOW()),
	 ('48GMMM',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('47LMMB',1,7,NOW()),
	 ('47LMMB',1,8,NOW()),
	 ('47LMMB',1,9,NOW()),
	 ('47LMMB',1,10,NOW()),
	 ('47LMMB',1,11,NOW()),
	 ('47LMMB',1,12,NOW()),
	 ('47LMMB',2,7,NOW()),
	 ('47LMMB',2,8,NOW()),
	 ('47LMMB',2,9,NOW()),
	 ('47LMMB',2,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('47LMMB',2,11,NOW()),
	 ('47LMMB',2,12,NOW()),
	 ('47LMMB',3,7,NOW()),
	 ('47LMMB',3,8,NOW()),
	 ('47LMMB',3,9,NOW()),
	 ('47LMMB',3,10,NOW()),
	 ('47LMMB',3,11,NOW()),
	 ('47LMMB',3,12,NOW()),
	 ('47LMMB',4,7,NOW()),
	 ('47LMMB',4,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('47LMMB',4,9,NOW()),
	 ('47LMMB',4,10,NOW()),
	 ('47LMMB',4,11,NOW()),
	 ('47LMMB',4,12,NOW()),
	 ('47LMMB',5,7,NOW()),
	 ('47LMMB',5,8,NOW()),
	 ('47LMMB',5,9,NOW()),
	 ('47LMMB',5,10,NOW()),
	 ('47LMMB',5,11,NOW()),
	 ('47LMMB',5,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('46LDNRS',1,7,NOW()),
	 ('46LDNRS',1,8,NOW()),
	 ('46LDNRS',1,9,NOW()),
	 ('46LDNRS',1,10,NOW()),
	 ('46LDNRS',2,7,NOW()),
	 ('46LDNRS',2,8,NOW()),
	 ('46LDNRS',2,9,NOW()),
	 ('46LDNRS',2,10,NOW()),
	 ('46LDNRS',3,7,NOW()),
	 ('46LDNRS',3,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('46LDNRS',3,9,NOW()),
	 ('46LDNRS',3,10,NOW()),
	 ('46LDNRS',4,7,NOW()),
	 ('46LDNRS',4,8,NOW()),
	 ('46LDNRS',4,9,NOW()),
	 ('46LDNRS',4,10,NOW()),
	 ('46LDNRS',5,7,NOW()),
	 ('46LDNRS',5,8,NOW()),
	 ('46LDNRS',5,9,NOW()),
	 ('46LDNRS',5,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('46JCRCY',1,7,NOW()),
	 ('46JCRCY',1,8,NOW()),
	 ('46JCRCY',1,9,NOW()),
	 ('46JCRCY',1,10,NOW()),
	 ('46JCRCY',2,7,NOW()),
	 ('46JCRCY',2,8,NOW()),
	 ('46JCRCY',2,9,NOW()),
	 ('46JCRCY',2,10,NOW()),
	 ('46JCRCY',3,7,NOW()),
	 ('46JCRCY',3,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('46JCRCY',3,9,NOW()),
	 ('46JCRCY',3,10,NOW()),
	 ('46JCRCY',4,7,NOW()),
	 ('46JCRCY',4,8,NOW()),
	 ('46JCRCY',4,9,NOW()),
	 ('46JCRCY',4,10,NOW()),
	 ('46JCRCY',5,7,NOW()),
	 ('46JCRCY',5,8,NOW()),
	 ('46JCRCY',5,9,NOW()),
	 ('46JCRCY',5,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('47FRSO',1,7,NOW()),
	 ('47FRSO',1,8,NOW()),
	 ('47FRSO',1,9,NOW()),
	 ('47FRSO',1,10,NOW()),
	 ('47FRSO',1,11,NOW()),
	 ('47FRSO',1,12,NOW()),
	 ('47FRSO',2,7,NOW()),
	 ('47FRSO',2,8,NOW()),
	 ('47FRSO',2,9,NOW()),
	 ('47FRSO',2,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('47FRSO',2,11,NOW()),
	 ('47FRSO',2,12,NOW()),
	 ('47FRSO',3,7,NOW()),
	 ('47FRSO',3,8,NOW()),
	 ('47FRSO',3,9,NOW()),
	 ('47FRSO',3,10,NOW()),
	 ('47FRSO',3,11,NOW()),
	 ('47FRSO',3,12,NOW()),
	 ('47FRSO',4,7,NOW()),
	 ('47FRSO',4,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('47FRSO',4,9,NOW()),
	 ('47FRSO',4,10,NOW()),
	 ('47FRSO',4,11,NOW()),
	 ('47FRSO',4,12,NOW()),
	 ('47FRSO',5,7,NOW()),
	 ('47FRSO',5,8,NOW()),
	 ('47FRSO',5,9,NOW()),
	 ('47FRSO',5,10,NOW()),
	 ('47FRSO',5,11,NOW()),
	 ('47FRSO',5,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48RRGB',1,7,NOW()),
	 ('48RRGB',1,8,NOW()),
	 ('48RRGB',1,9,NOW()),
	 ('48RRGB',1,10,NOW()),
	 ('48RRGB',1,11,NOW()),
	 ('48RRGB',1,12,NOW()),
	 ('48RRGB',1,13,NOW()),
	 ('48RRGB',1,14,NOW()),
	 ('48RRGB',2,7,NOW()),
	 ('48RRGB',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48RRGB',2,9,NOW()),
	 ('48RRGB',2,10,NOW()),
	 ('48RRGB',2,11,NOW()),
	 ('48RRGB',2,12,NOW()),
	 ('48RRGB',2,13,NOW()),
	 ('48RRGB',2,14,NOW()),
	 ('48RRGB',3,7,NOW()),
	 ('48RRGB',3,8,NOW()),
	 ('48RRGB',3,9,NOW()),
	 ('48RRGB',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48RRGB',3,11,NOW()),
	 ('48RRGB',3,12,NOW()),
	 ('48RRGB',3,13,NOW()),
	 ('48RRGB',3,14,NOW()),
	 ('48RRGB',4,7,NOW()),
	 ('48RRGB',4,8,NOW()),
	 ('48RRGB',4,9,NOW()),
	 ('48RRGB',4,10,NOW()),
	 ('48RRGB',4,11,NOW()),
	 ('48RRGB',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48RRGB',4,13,NOW()),
	 ('48RRGB',4,14,NOW()),
	 ('48RRGB',5,7,NOW()),
	 ('48RRGB',5,8,NOW()),
	 ('48RRGB',5,9,NOW()),
	 ('48RRGB',5,10,NOW()),
	 ('48RRGB',5,11,NOW()),
	 ('48RRGB',5,12,NOW()),
	 ('48RRGB',5,13,NOW()),
	 ('48RRGB',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ERNE',1,7,NOW()),
	 ('48ERNE',1,8,NOW()),
	 ('48ERNE',1,9,NOW()),
	 ('48ERNE',1,10,NOW()),
	 ('48ERNE',1,11,NOW()),
	 ('48ERNE',1,12,NOW()),
	 ('48ERNE',1,13,NOW()),
	 ('48ERNE',1,14,NOW()),
	 ('48ERNE',2,7,NOW()),
	 ('48ERNE',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ERNE',2,9,NOW()),
	 ('48ERNE',2,10,NOW()),
	 ('48ERNE',2,11,NOW()),
	 ('48ERNE',2,12,NOW()),
	 ('48ERNE',2,13,NOW()),
	 ('48ERNE',2,14,NOW()),
	 ('48ERNE',3,7,NOW()),
	 ('48ERNE',3,8,NOW()),
	 ('48ERNE',3,9,NOW()),
	 ('48ERNE',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ERNE',3,11,NOW()),
	 ('48ERNE',3,12,NOW()),
	 ('48ERNE',3,13,NOW()),
	 ('48ERNE',3,14,NOW()),
	 ('48ERNE',4,7,NOW()),
	 ('48ERNE',4,8,NOW()),
	 ('48ERNE',4,9,NOW()),
	 ('48ERNE',4,10,NOW()),
	 ('48ERNE',4,11,NOW()),
	 ('48ERNE',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ERNE',4,13,NOW()),
	 ('48ERNE',4,14,NOW()),
	 ('48ERNE',5,7,NOW()),
	 ('48ERNE',5,8,NOW()),
	 ('48ERNE',5,9,NOW()),
	 ('48ERNE',5,10,NOW()),
	 ('48ERNE',5,11,NOW()),
	 ('48ERNE',5,12,NOW()),
	 ('48ERNE',5,13,NOW()),
	 ('48ERNE',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48PBRAU',1,7,NOW()),
	 ('48PBRAU',1,8,NOW()),
	 ('48PBRAU',1,9,NOW()),
	 ('48PBRAU',1,10,NOW()),
	 ('48PBRAU',1,11,NOW()),
	 ('48PBRAU',1,12,NOW()),
	 ('48PBRAU',1,13,NOW()),
	 ('48PBRAU',1,14,NOW()),
	 ('48PBRAU',2,7,NOW()),
	 ('48PBRAU',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48PBRAU',2,9,NOW()),
	 ('48PBRAU',2,10,NOW()),
	 ('48PBRAU',2,11,NOW()),
	 ('48PBRAU',2,12,NOW()),
	 ('48PBRAU',2,13,NOW()),
	 ('48PBRAU',2,14,NOW()),
	 ('48PBRAU',3,7,NOW()),
	 ('48PBRAU',3,8,NOW()),
	 ('48PBRAU',3,9,NOW()),
	 ('48PBRAU',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48PBRAU',3,11,NOW()),
	 ('48PBRAU',3,12,NOW()),
	 ('48PBRAU',3,13,NOW()),
	 ('48PBRAU',3,14,NOW()),
	 ('48PBRAU',4,7,NOW()),
	 ('48PBRAU',4,8,NOW()),
	 ('48PBRAU',4,9,NOW()),
	 ('48PBRAU',4,10,NOW()),
	 ('48PBRAU',4,11,NOW()),
	 ('48PBRAU',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48PBRAU',4,13,NOW()),
	 ('48PBRAU',4,14,NOW()),
	 ('48PBRAU',5,7,NOW()),
	 ('48PBRAU',5,8,NOW()),
	 ('48PBRAU',5,9,NOW()),
	 ('48PBRAU',5,10,NOW()),
	 ('48PBRAU',5,11,NOW()),
	 ('48PBRAU',5,12,NOW()),
	 ('48PBRAU',5,13,NOW()),
	 ('48PBRAU',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ASOG',1,7,NOW()),
	 ('48ASOG',1,8,NOW()),
	 ('48ASOG',1,9,NOW()),
	 ('48ASOG',1,10,NOW()),
	 ('48ASOG',1,11,NOW()),
	 ('48ASOG',1,12,NOW()),
	 ('48ASOG',1,13,NOW()),
	 ('48ASOG',1,14,NOW()),
	 ('48ASOG',2,7,NOW()),
	 ('48ASOG',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ASOG',2,9,NOW()),
	 ('48ASOG',2,10,NOW()),
	 ('48ASOG',2,11,NOW()),
	 ('48ASOG',2,12,NOW()),
	 ('48ASOG',2,13,NOW()),
	 ('48ASOG',2,14,NOW()),
	 ('48ASOG',3,7,NOW()),
	 ('48ASOG',3,8,NOW()),
	 ('48ASOG',3,9,NOW()),
	 ('48ASOG',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ASOG',3,11,NOW()),
	 ('48ASOG',3,12,NOW()),
	 ('48ASOG',3,13,NOW()),
	 ('48ASOG',3,14,NOW()),
	 ('48ASOG',4,7,NOW()),
	 ('48ASOG',4,8,NOW()),
	 ('48ASOG',4,9,NOW()),
	 ('48ASOG',4,10,NOW()),
	 ('48ASOG',4,11,NOW()),
	 ('48ASOG',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ASOG',4,13,NOW()),
	 ('48ASOG',4,14,NOW()),
	 ('48ASOG',5,7,NOW()),
	 ('48ASOG',5,8,NOW()),
	 ('48ASOG',5,9,NOW()),
	 ('48ASOG',5,10,NOW()),
	 ('48ASOG',5,11,NOW()),
	 ('48ASOG',5,12,NOW()),
	 ('48ASOG',5,13,NOW()),
	 ('48ASOG',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ASMO',1,7,NOW()),
	 ('48ASMO',1,8,NOW()),
	 ('48ASMO',1,9,NOW()),
	 ('48ASMO',1,10,NOW()),
	 ('48ASMO',1,11,NOW()),
	 ('48ASMO',1,12,NOW()),
	 ('48ASMO',1,13,NOW()),
	 ('48ASMO',1,14,NOW()),
	 ('48ASMO',2,7,NOW()),
	 ('48ASMO',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ASMO',2,9,NOW()),
	 ('48ASMO',2,10,NOW()),
	 ('48ASMO',2,11,NOW()),
	 ('48ASMO',2,12,NOW()),
	 ('48ASMO',2,13,NOW()),
	 ('48ASMO',2,14,NOW()),
	 ('48ASMO',3,7,NOW()),
	 ('48ASMO',3,8,NOW()),
	 ('48ASMO',3,9,NOW()),
	 ('48ASMO',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ASMO',3,11,NOW()),
	 ('48ASMO',3,12,NOW()),
	 ('48ASMO',3,13,NOW()),
	 ('48ASMO',3,14,NOW()),
	 ('48ASMO',4,7,NOW()),
	 ('48ASMO',4,8,NOW()),
	 ('48ASMO',4,9,NOW()),
	 ('48ASMO',4,10,NOW()),
	 ('48ASMO',4,11,NOW()),
	 ('48ASMO',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ASMO',4,13,NOW()),
	 ('48ASMO',4,14,NOW()),
	 ('48ASMO',5,7,NOW()),
	 ('48ASMO',5,8,NOW()),
	 ('48ASMO',5,9,NOW()),
	 ('48ASMO',5,10,NOW()),
	 ('48ASMO',5,11,NOW()),
	 ('48ASMO',5,12,NOW()),
	 ('48ASMO',5,13,NOW()),
	 ('48ASMO',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48LSCS',1,7,NOW()),
	 ('48LSCS',1,8,NOW()),
	 ('48LSCS',1,9,NOW()),
	 ('48LSCS',1,10,NOW()),
	 ('48LSCS',1,11,NOW()),
	 ('48LSCS',1,12,NOW()),
	 ('48LSCS',1,13,NOW()),
	 ('48LSCS',1,14,NOW()),
	 ('48LSCS',2,7,NOW()),
	 ('48LSCS',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48LSCS',2,9,NOW()),
	 ('48LSCS',2,10,NOW()),
	 ('48LSCS',2,11,NOW()),
	 ('48LSCS',2,12,NOW()),
	 ('48LSCS',2,13,NOW()),
	 ('48LSCS',2,14,NOW()),
	 ('48LSCS',3,7,NOW()),
	 ('48LSCS',3,8,NOW()),
	 ('48LSCS',3,9,NOW()),
	 ('48LSCS',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48LSCS',3,11,NOW()),
	 ('48LSCS',3,12,NOW()),
	 ('48LSCS',3,13,NOW()),
	 ('48LSCS',3,14,NOW()),
	 ('48LSCS',4,7,NOW()),
	 ('48LSCS',4,8,NOW()),
	 ('48LSCS',4,9,NOW()),
	 ('48LSCS',4,10,NOW()),
	 ('48LSCS',4,11,NOW()),
	 ('48LSCS',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48LSCS',4,13,NOW()),
	 ('48LSCS',4,14,NOW()),
	 ('48LSCS',5,7,NOW()),
	 ('48LSCS',5,8,NOW()),
	 ('48LSCS',5,9,NOW()),
	 ('48LSCS',5,10,NOW()),
	 ('48LSCS',5,11,NOW()),
	 ('48LSCS',5,12,NOW()),
	 ('48LSCS',5,13,NOW()),
	 ('48LSCS',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ISOT',1,7,NOW()),
	 ('48ISOT',1,8,NOW()),
	 ('48ISOT',1,9,NOW()),
	 ('48ISOT',1,10,NOW()),
	 ('48ISOT',1,11,NOW()),
	 ('48ISOT',1,12,NOW()),
	 ('48ISOT',1,13,NOW()),
	 ('48ISOT',1,14,NOW()),
	 ('48ISOT',2,7,NOW()),
	 ('48ISOT',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ISOT',2,9,NOW()),
	 ('48ISOT',2,10,NOW()),
	 ('48ISOT',2,11,NOW()),
	 ('48ISOT',2,12,NOW()),
	 ('48ISOT',2,13,NOW()),
	 ('48ISOT',2,14,NOW()),
	 ('48ISOT',3,7,NOW()),
	 ('48ISOT',3,8,NOW()),
	 ('48ISOT',3,9,NOW()),
	 ('48ISOT',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ISOT',3,11,NOW()),
	 ('48ISOT',3,12,NOW()),
	 ('48ISOT',3,13,NOW()),
	 ('48ISOT',3,14,NOW()),
	 ('48ISOT',4,7,NOW()),
	 ('48ISOT',4,8,NOW()),
	 ('48ISOT',4,9,NOW()),
	 ('48ISOT',4,10,NOW()),
	 ('48ISOT',4,11,NOW()),
	 ('48ISOT',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ISOT',4,13,NOW()),
	 ('48ISOT',4,14,NOW()),
	 ('48ISOT',5,7,NOW()),
	 ('48ISOT',5,8,NOW()),
	 ('48ISOT',5,9,NOW()),
	 ('48ISOT',5,10,NOW()),
	 ('48ISOT',5,11,NOW()),
	 ('48ISOT',5,12,NOW()),
	 ('48ISOT',5,13,NOW()),
	 ('48ISOT',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ASOE',1,7,NOW()),
	 ('48ASOE',1,8,NOW()),
	 ('48ASOE',1,9,NOW()),
	 ('48ASOE',1,10,NOW()),
	 ('48ASOE',1,11,NOW()),
	 ('48ASOE',1,12,NOW()),
	 ('48ASOE',1,13,NOW()),
	 ('48ASOE',1,14,NOW()),
	 ('48ASOE',2,7,NOW()),
	 ('48ASOE',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ASOE',2,9,NOW()),
	 ('48ASOE',2,10,NOW()),
	 ('48ASOE',2,11,NOW()),
	 ('48ASOE',2,12,NOW()),
	 ('48ASOE',2,13,NOW()),
	 ('48ASOE',2,14,NOW()),
	 ('48ASOE',3,7,NOW()),
	 ('48ASOE',3,8,NOW()),
	 ('48ASOE',3,9,NOW()),
	 ('48ASOE',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ASOE',3,11,NOW()),
	 ('48ASOE',3,12,NOW()),
	 ('48ASOE',3,13,NOW()),
	 ('48ASOE',3,14,NOW()),
	 ('48ASOE',4,7,NOW()),
	 ('48ASOE',4,8,NOW()),
	 ('48ASOE',4,9,NOW()),
	 ('48ASOE',4,10,NOW()),
	 ('48ASOE',4,11,NOW()),
	 ('48ASOE',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48ASOE',4,13,NOW()),
	 ('48ASOE',4,14,NOW()),
	 ('48ASOE',5,7,NOW()),
	 ('48ASOE',5,8,NOW()),
	 ('48ASOE',5,9,NOW()),
	 ('48ASOE',5,10,NOW()),
	 ('48ASOE',5,11,NOW()),
	 ('48ASOE',5,12,NOW()),
	 ('48ASOE',5,13,NOW()),
	 ('48ASOE',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('46MASOC',1,7,NOW()),
	 ('46MASOC',1,8,NOW()),
	 ('46MASOC',1,9,NOW()),
	 ('46MASOC',1,10,NOW()),
	 ('46MASOC',2,7,NOW()),
	 ('46MASOC',2,8,NOW()),
	 ('46MASOC',2,9,NOW()),
	 ('46MASOC',2,10,NOW()),
	 ('46MASOC',3,7,NOW()),
	 ('46MASOC',3,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('46MASOC',3,9,NOW()),
	 ('46MASOC',3,10,NOW()),
	 ('46MASOC',4,7,NOW()),
	 ('46MASOC',4,8,NOW()),
	 ('46MASOC',4,9,NOW()),
	 ('46MASOC',4,10,NOW()),
	 ('46MASOC',5,7,NOW()),
	 ('46MASOC',5,8,NOW()),
	 ('46MASOC',5,9,NOW()),
	 ('46MASOC',5,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48YESMR',1,7,NOW()),
	 ('48YESMR',1,8,NOW()),
	 ('48YESMR',1,9,NOW()),
	 ('48YESMR',1,10,NOW()),
	 ('48YESMR',1,11,NOW()),
	 ('48YESMR',1,12,NOW()),
	 ('48YESMR',1,13,NOW()),
	 ('48YESMR',1,14,NOW()),
	 ('48YESMR',2,7,NOW()),
	 ('48YESMR',2,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48YESMR',2,9,NOW()),
	 ('48YESMR',2,10,NOW()),
	 ('48YESMR',2,11,NOW()),
	 ('48YESMR',2,12,NOW()),
	 ('48YESMR',2,13,NOW()),
	 ('48YESMR',2,14,NOW()),
	 ('48YESMR',3,7,NOW()),
	 ('48YESMR',3,8,NOW()),
	 ('48YESMR',3,9,NOW()),
	 ('48YESMR',3,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48YESMR',3,11,NOW()),
	 ('48YESMR',3,12,NOW()),
	 ('48YESMR',3,13,NOW()),
	 ('48YESMR',3,14,NOW()),
	 ('48YESMR',4,7,NOW()),
	 ('48YESMR',4,8,NOW()),
	 ('48YESMR',4,9,NOW()),
	 ('48YESMR',4,10,NOW()),
	 ('48YESMR',4,11,NOW()),
	 ('48YESMR',4,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('48YESMR',4,13,NOW()),
	 ('48YESMR',4,14,NOW()),
	 ('48YESMR',5,7,NOW()),
	 ('48YESMR',5,8,NOW()),
	 ('48YESMR',5,9,NOW()),
	 ('48YESMR',5,10,NOW()),
	 ('48YESMR',5,11,NOW()),
	 ('48YESMR',5,12,NOW()),
	 ('48YESMR',5,13,NOW()),
	 ('48YESMR',5,14,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('46IUAW',1,7,NOW()),
	 ('46IUAW',1,8,NOW()),
	 ('46IUAW',1,9,NOW()),
	 ('46IUAW',1,10,NOW()),
	 ('46IUAW',2,7,NOW()),
	 ('46IUAW',2,8,NOW()),
	 ('46IUAW',2,9,NOW()),
	 ('46IUAW',2,10,NOW()),
	 ('46IUAW',3,7,NOW()),
	 ('46IUAW',3,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('46IUAW',3,9,NOW()),
	 ('46IUAW',3,10,NOW()),
	 ('46IUAW',4,7,NOW()),
	 ('46IUAW',4,8,NOW()),
	 ('46IUAW',4,9,NOW()),
	 ('46IUAW',4,10,NOW()),
	 ('46IUAW',5,7,NOW()),
	 ('46IUAW',5,8,NOW()),
	 ('46IUAW',5,9,NOW()),
	 ('46IUAW',5,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('47SVPE',1,7,NOW()),
	 ('47SVPE',1,8,NOW()),
	 ('47SVPE',1,9,NOW()),
	 ('47SVPE',1,10,NOW()),
	 ('47SVPE',1,11,NOW()),
	 ('47SVPE',1,12,NOW()),
	 ('47SVPE',2,7,NOW()),
	 ('47SVPE',2,8,NOW()),
	 ('47SVPE',2,9,NOW()),
	 ('47SVPE',2,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('47SVPE',2,11,NOW()),
	 ('47SVPE',2,12,NOW()),
	 ('47SVPE',3,7,NOW()),
	 ('47SVPE',3,8,NOW()),
	 ('47SVPE',3,9,NOW()),
	 ('47SVPE',3,10,NOW()),
	 ('47SVPE',3,11,NOW()),
	 ('47SVPE',3,12,NOW()),
	 ('47SVPE',4,7,NOW()),
	 ('47SVPE',4,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('47SVPE',4,9,'2026-01-21 19:24:04.873'),
	 ('47SVPE',4,10,NOW()),
	 ('47SVPE',4,11,NOW()),
	 ('47SVPE',4,12,NOW()),
	 ('47SVPE',5,7,NOW()),
	 ('47SVPE',5,8,NOW()),
	 ('47SVPE',5,9,NOW()),
	 ('47SVPE',5,10,NOW()),
	 ('47SVPE',5,11,NOW()),
	 ('47SVPE',5,12,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('46YVVGG',1,7,NOW()),
	 ('46YVVGG',1,8,NOW()),
	 ('46YVVGG',1,9,NOW()),
	 ('46YVVGG',1,10,NOW()),
	 ('46YVVGG',2,7,NOW()),
	 ('46YVVGG',2,8,NOW()),
	 ('46YVVGG',2,9,NOW()),
	 ('46YVVGG',2,10,NOW()),
	 ('46YVVGG',3,7,NOW()),
	 ('46YVVGG',3,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('46YVVGG',3,9,NOW()),
	 ('46YVVGG',3,10,NOW()),
	 ('46YVVGG',4,7,NOW()),
	 ('46YVVGG',4,8,NOW()),
	 ('46YVVGG',4,9,NOW()),
	 ('46YVVGG',4,10,NOW()),
	 ('46YVVGG',5,7,NOW()),
	 ('46YVVGG',5,8,NOW()),
	 ('46YVVGG',5,9,NOW()),
	 ('46YVVGG',5,10,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('46IZMO',1,7,NOW()),
	 ('46IZMO',1,8,NOW()),
	 ('46IZMO',1,9,NOW()),
	 ('46IZMO',1,10,NOW()),
	 ('46IZMO',2,7,NOW()),
	 ('46IZMO',2,8,NOW()),
	 ('46IZMO',2,9,NOW()),
	 ('46IZMO',2,10,NOW()),
	 ('46IZMO',3,7,NOW()),
	 ('46IZMO',3,8,NOW());
INSERT INTO teacher_availability (teacher_id,day_of_week,"hour",created_at) VALUES
	 ('46IZMO',3,9,NOW()),
	 ('46IZMO',3,10,NOW()),
	 ('46IZMO',4,7,NOW()),
	 ('46IZMO',4,8,NOW()),
	 ('46IZMO',4,9,NOW()),
	 ('46IZMO',4,10,NOW()),
	 ('46IZMO',5,7,NOW()),
	 ('46IZMO',5,8,NOW()),
	 ('46IZMO',5,9,NOW()),
	 ('46IZMO',5,10,NOW());

commit;


INSERT INTO teacher_qualification (teacher_id,qualification,created_at) VALUES
	 ('48JAABQ','PENSAMIENTO MATEMATICO II',NOW()),
	 ('48DLAOV','REACCIONES QUIMICAS: CONSERVACION DE LA MATERIA EN LA FORMACION DE NUEVAS SUSTANCIAS',NOW()),
	 ('48DLAOV','INTERACCIONES HUMANAS CON LA NATURALEZA',NOW()),
	 ('48SLAMC','REALIZA ANALISIS FISICOS Y QUIMICOS A LA MATERIA PRIMA',NOW()),
	 ('48SLAMC','REALIZA ANALISIS MICROBIOLOGICOS A LA MATERIA PRIMA',NOW()),
	 ('48SLAMC','TUTORIAS III',NOW()),
	 ('48JBWT','RECURSO SOCIOEMOCIONAL II',NOW()),
	 ('48JBWT','HUMANIDADES III',NOW()),
	 ('48ABCJ','VERIFICA LA DOCUMENTACION PARA LA IMPORTACION Y EXPORTACION DE MERCANCIAS',NOW()),
	 ('48ABCJ','RECURSO SOCIOEMOCIONAL II',NOW());
INSERT INTO teacher_qualification (teacher_id,qualification,created_at) VALUES
	 ('48ABCJ','PROMUEVE CONDICIONES DE TRABAJO SALUDABLES EN LA ORGANIZACION',NOW()),
	 ('48ABCJ','AUXILIA EN EL CÁLCULO DE LA NOMINA ORDINARIA',NOW()),
	 ('46ICJM','TEMAS SELECTOS DE MATEMATICAS I',NOW()),
	 ('46IJCAI','RECURSO SOCIOEMOCIONAL II',NOW()),
	 ('46IJCAI','RECURSOS SOCIOEMOCIONALES III',NOW()),
	 ('46IJCAI','MANTIENE EQUIPOS DE REFRIGERACION',NOW()),
	 ('48JACAJ','INGLES II',NOW()),
	 ('48JACAJ','ORGANISMOS: ESTRUCTURAS Y PROCESOS. HERENCIA Y EVOLUCIÓN BIOLÓGICA',NOW()),
	 ('48BCCS','PROGRAMA PLC PARA SISTEMAS AUTOMATIZADOS',NOW()),
	 ('46FNDZG','INSTALA SISTEMAS ELECTRONICOS INDUSTRIALES AUTOMATIZADOS',NOW());
INSERT INTO teacher_qualification (teacher_id,qualification,created_at) VALUES
	 ('46FNDZG','REALIZA MANTENIMIENTO A SISTEMAS ELECTRICOS DE POTENCIA',NOW()),
	 ('47YAMSC','CIENCIAS SOCIALES II',NOW()),
	 ('46REJA','INGLES II',NOW()),
	 ('48LFGD','RECURSO SOCIOEMOCIONAL II',NOW()),
	 ('48LFGD','DISEÑA SOFTWARE DE SISTEMAS INFORMATICOS',NOW()),
	 ('48LFGD','CODIFICA SOFTWARE DE SISTEMAS INFORMATICOS',NOW()),
	 ('48LFGD','IMPLEMENTA SOFTWARE DE SISTEMAS INFORMATICOS',NOW()),
	 ('47CGHV','REALIZA INSTALACIONES DE CIRCUITOS ELECTRONICOS EN SISTEMAS ELECTROMECANICOS',NOW()),
	 ('47CGHV','CONSTRUYE ESTRUCTURAS METALICAS PARA LA INDUSTRIA',NOW()),
	 ('47CGHV','TUTORIAS III',NOW());
INSERT INTO teacher_qualification (teacher_id,qualification,created_at) VALUES
	 ('48HGRO','CORRIGE VULNERABILIDADES EN SISTEMAS INFORMATICOS',NOW()),
	 ('48HGRO','TUTORIAS IV',NOW()),
	 ('48HGRO','PENSAMIENTO MATEMATICO II',NOW()),
	 ('48HGRO','IMPLEMENTA SCRIPTS EN UN LENGUAJE DE PROGRAMACION PARA SOL DE PROB DE SEGURIDAD',NOW()),
	 ('48MAGCE','CIENCIAS SOCIALES II',NOW()),
	 ('48MAGCE','CIENCIAS SOCIALES III',NOW()),
	 ('48MAGCE','RECURSO SOCIOEMOCIONAL II',NOW()),
	 ('48YHGN','CIENCIAS NATURALES, EXPERIMENTALES Y TECNOLOGIA II. EL PODER DE LA ENERGIA.',NOW()),
	 ('48AJAP','TEMAS SELECTOS DE MATEMATICAS I',NOW()),
	 ('48AJAP','TEMAS SELECTOS DE MATEMATICAS III',NOW());
INSERT INTO teacher_qualification (teacher_id,qualification,created_at) VALUES
	 ('48DRLSO','PENSAMIENTO MATEMATICO II',NOW()),
	 ('48DRLSO','TEMAS SELECTOS DE MATEMATICAS III',NOW()),
	 ('48GMMM','LENGUA Y COMUNICACION II',NOW()),
	 ('47LMMB','CIENCIAS SOCIALES II',NOW()),
	 ('47LMMB','RECURSOS SOCIOEMOCIONALES III',NOW()),
	 ('47LMMB','TUTORIAS IV',NOW()),
	 ('47LMMB','DETECTA VULNERABILIDADES EN SISTEMAS INFORMATICOS',NOW()),
	 ('46LDNRS','RECURSO SOCIOEMOCIONAL II',NOW()),
	 ('46LDNRS','REACCIONES QUIMICAS: CONSERVACION DE LA MATERIA EN LA FORMACION DE NUEVAS SUSTANCIAS',NOW()),
	 ('46LDNRS','INTERACCIONES HUMANAS CON LA NATURALEZA',NOW());
INSERT INTO teacher_qualification (teacher_id,qualification,created_at) VALUES
	 ('46JCRCY','CULTURA DIGITAL II',NOW()),
	 ('47FRSO','AUXILIA EN PROCEDIMIENTOS ADMINISTRATIVOS Y NORMATIVOS PARA IMPORTACIONES Y EXPORTACIONES DE MERCANCIAS',NOW()),
	 ('48RRGB','RECURSOS SOCIOEMOCIONALES III',NOW()),
	 ('48RRGB','TUTORIAS IV',NOW()),
	 ('48RRGB','MAQUINA PIEZAS MECANICAS EN TORNO Y FRESADORA CONVENCIONAL',NOW()),
	 ('48RRGB','MAQUINA PIEZAS MECANICAS EN TORNO Y FRESADORA CNC',NOW()),
	 ('48ERNE','ELABORA PROYECTOS CON PROGRAMACION LOGICA',NOW()),
	 ('48ERNE','RECURSOS SOCIOEMOCIONALES III',NOW()),
	 ('48ERNE','TUTORIAS IV',NOW()),
	 ('48ERNE','IMPLEMENTA BASE DE DATOS RELACIONALES EN UN SISTEMA DE INFORMACION',NOW());
INSERT INTO teacher_qualification (teacher_id,qualification,created_at) VALUES
	 ('48PBRAU','REALIZA INSTALACIONES ELECTRICAS EN EQUIPOS ELECTROMECANICOS',NOW()),
	 ('48PBRAU','RECURSO SOCIOEMOCIONAL II',NOW()),
	 ('48ASOG','CIENCIAS NATURALES, EXPERIMENTALES Y TECNOLOGIA II. EL PODER DE LA ENERGIA.',NOW()),
	 ('48ASMO','CONCIENCIA HISTORICA I',NOW()),
	 ('48ASMO','CONCIENCIA HISTÓRICA. LA REALIDAD ACTUAL EN PERSPECTIVA HISTORICA.',NOW()),
	 ('45EUSAS','LENGUA Y COMUNICACION II',NOW()),
	 ('48LSCS','TEMAS SELECTOS DE MATEMATICAS I',NOW()),
	 ('48LSCS','PENSAMIENTO MATEMATICO II',NOW()),
	 ('48ISOT','CULTURA DIGITAL II',NOW()),
	 ('48ISOT','DISEÑA ALGORITMOS DE PROBLEMAS DE SEGURIDAD',NOW());
INSERT INTO teacher_qualification (teacher_id,qualification,created_at) VALUES
	 ('48ISOT','TUTORIAS III',NOW()),
	 ('48ISOT','DISEÑA APLICACIONES MOVILES MULTIPLATAFORMA',NOW()),
	 ('45LDLSRP','CONCIENCIA HISTORICA I',NOW()),
	 ('45LDLSRP','HUMANISMO Y PENSAMIENTO FILOSÓFICO EN MÉXICO',NOW()),
	 ('48ASOE','CIENCIAS NATURALES, EXPERIMENTALES Y TECNOLOGIA II. EL PODER DE LA ENERGIA.',NOW()),
	 ('48ASOE','ORGANISMOS: ESTRUCTURAS Y PROCESOS. HERENCIA Y EVOLUCIÓN BIOLÓGICA',NOW()),
	 ('48ASOE','INTERACCIONES HUMANAS CON LA NATURALEZA',NOW()),
	 ('46MASOC','DISEÑA PLANOS Y DIAGRAMAS ELECTRICOS Y ELECTRONICOS DE SISTEMAS ELECTROMECANICOS',NOW()),
	 ('48YESMR','REALIZA ANALISIS FISICOS Y QUIMICOS A LA MATERIA PRIMA',NOW()),
	 ('48YESMR','REALIZA ANALISIS MICROBIOLOGICOS A LA MATERIA PRIMA',NOW());
INSERT INTO teacher_qualification (teacher_id,qualification,created_at) VALUES
	 ('48YESMR','TRANSFORMA CARNE Y SUS DERIVADOS EN PRODUCTOS ALIMENTICIOS',NOW()),
	 ('48YESMR','TUTORIAS IV',NOW()),
	 ('46IUAW','REALIZA ANALISIS FISICOS, QUIMICOS Y MICROBIOLOGICOS EN CARNES Y SUS DERIVADOS',NOW()),
	 ('46IUAW','INTERACCIONES HUMANAS CON LA NATURALEZA',NOW()),
	 ('46IUAW','REALIZA LOS ANÁLISIS FÍSICOS, QUÍMICOS Y MICROBIOLÓGICOS DE LOS PRODUCTOS DE CEREALES U OLEAGINOSAS Y PRODUCTOS DERIVADOS',NOW()),
	 ('47SVPE','EJECUTA PROCEDIMIENTOS ADMINISTRATIVOS DEL AREA DE RECURSOS HUMANOS',NOW()),
	 ('47SVPE','GESTIONA DOCUMENTACION DEL AREA DE RECURSOS HUMANOS',NOW()),
	 ('47SVPE','RECURSOS SOCIOEMOCIONALES III',NOW()),
	 ('47SVPE','TUTORIAS IV',NOW()),
	 ('46YVVGG','INGLES IV',NOW());
INSERT INTO teacher_qualification (teacher_id,qualification,created_at) VALUES
	 ('45MVVT','CULTURA DIGITAL II',NOW()),
	 ('45MVVT','DESARROLLA ALGORITMOS PARA SOLUCIONAR PROBLEMAS',NOW()),
	 ('46IZMO','REACCIONES QUIMICAS: CONSERVACION DE LA MATERIA EN LA FORMACION DE NUEVAS SUSTANCIAS',NOW()),
	 ('47SVPE','GESTIONA LOS PROCESOS DE CAPACITACION PARA EL DESARROLLO DEL TALENTO HUMANO',NOW()),
	 ('48ERNE','IMPLEMENTA BASE DE DATOS NO RELACIONALES EN UN SISTEMA DE INFORMACION',NOW()),
	 ('48HGRO','TUTORIAS III',NOW()),
	 ('48ABCJ','AUXILIA EN EL CÁLCULO DE LA NOMINA EXTRAORDINARIA',NOW()),
	 ('48YESMR','REALIZA LOS PROCESOS DE TRANSFORMACIÓN DE CEREALES Y PRODUCTOS DERIVADOS',NOW()),
	 ('48RRGB','MANTIENE EQUIPOS HIDRAULICOS',NOW()),
	 ('48RRGB','MANTIENE EQUIPOS NEUMATICOS',NOW());
INSERT INTO teacher_qualification (teacher_id,qualification,created_at) VALUES
	 ('48HGRO','INSTALA SISTEMAS ELECTRONICOS DOMOTICOS',NOW()),
	 ('47FRSO','TUTORIAS III',NOW()),
	 ('48LFGD','IMPLEMENTA APLICACIONES MOVILES MULTIPLATAFORMA',NOW());

commit;


INSERT INTO student_group (id,"name",preferred_room_name,created_at,updated_at) VALUES
	 ('2AARH','2AARH ADMINISTRACION DE REC HUMANOS','SALON 7',NOW(),NOW()),
	 ('2ACIA','2ACIA COMERCIO INTERNACIONAL Y ADUANAS','SALON 5',NOW(),NOW()),
	 ('2APIA','2APIA PRODUCCION INDUSTRIAL DE ALIMENTOS','SALON 6',NOW(),NOW()),
	 ('2BPIA','2BPIA PRODUCCION INDUSTRIAL DE ALIMENTOS','SALON 8',NOW(),NOW()),
	 ('2ATEM','2ATEM ELECTROMECANICA','SALON 9',NOW(),NOW()),
	 ('2BTEM','2BTEM ELECTROMECANICA','SALON 10',NOW(),NOW()),
	 ('2ACSG','2ACSG CIBERSEGURIDAD','SALON 11',NOW(),NOW()),
	 ('2APRO','2APRO PROGRAMACION','SALON 12',NOW(),NOW()),
	 ('2ATIA','2ATIA INTELIGENCIA ARTIFICIAL','SALON 13',NOW(),NOW()),
	 ('4AARH','4AARH ADMINISTRACION DE REC HUMANOS','SALON 14',NOW(),NOW());
INSERT INTO student_group (id,"name",preferred_room_name,created_at,updated_at) VALUES
	 ('4APIA','4APIA PRODUCCION INDUSTRIAL DE ALIMENTOS','SALON 15',NOW(),NOW()),
	 ('4ATEM','4ATEM ELECTROMECANICA','SALON 16',NOW(),NOW()),
	 ('4ATEC','4ATEC ELECTRONICA','SALON 17',NOW(),NOW()),
	 ('4APRO','4APRO PROGRAMACION','SALON 18',NOW(),NOW()),
	 ('4ATCS','4ATCS CIBERSEGURIDAD','SALON 19',NOW(),NOW()),
	 ('6ADRH','6ADRH REC HUMANOS','SALON 20',NOW(),NOW()),
	 ('6AIAL','6AIAL INDUSTRIALIZACION DE ALIMENTOS','SALON 21',NOW(),NOW()),
	 ('6ATEM','6ATEM ELECTROMECANICA','SALON 22',NOW(),NOW()),
	 ('6ATEC','6ATEC ELECTRONICA','SALON 17',NOW(),NOW()),
	 ('6APRG','6APRG PROGRAMACION','SALON 23',NOW(),NOW());
	 
commit;


INSERT INTO group_course (group_id,course_name,created_at) VALUES
	 ('2AARH','PENSAMIENTO MATEMATICO II',NOW()),
	 ('2AARH','INGLES II',NOW()),
	 ('2AARH','CIENCIAS NATURALES, EXPERIMENTALES Y TECNOLOGIA II. EL PODER DE LA ENERGIA.',NOW()),
	 ('2AARH','LENGUA Y COMUNICACION II',NOW()),
	 ('2AARH','CIENCIAS SOCIALES II',NOW()),
	 ('2AARH','CULTURA DIGITAL II',NOW()),
	 ('2AARH','RECURSO SOCIOEMOCIONAL II',NOW()),
	 ('2ACIA','PENSAMIENTO MATEMATICO II',NOW()),
	 ('2ACIA','INGLES II',NOW()),
	 ('2ACIA','CIENCIAS NATURALES, EXPERIMENTALES Y TECNOLOGIA II. EL PODER DE LA ENERGIA.',NOW());
INSERT INTO group_course (group_id,course_name,created_at) VALUES
	 ('2ACIA','LENGUA Y COMUNICACION II',NOW()),
	 ('2ACIA','CIENCIAS SOCIALES II',NOW()),
	 ('2ACIA','CULTURA DIGITAL II',NOW()),
	 ('2ACIA','RECURSO SOCIOEMOCIONAL II',NOW()),
	 ('2ACIA','AUXILIA EN PROCEDIMIENTOS ADMINISTRATIVOS Y NORMATIVOS PARA IMPORTACIONES Y EXPORTACIONES DE MERCANCIAS',NOW()),
	 ('2ACIA','VERIFICA LA DOCUMENTACION PARA LA IMPORTACION Y EXPORTACION DE MERCANCIAS',NOW()),
	 ('2APIA','PENSAMIENTO MATEMATICO II',NOW()),
	 ('2APIA','INGLES II',NOW()),
	 ('2APIA','CIENCIAS NATURALES, EXPERIMENTALES Y TECNOLOGIA II. EL PODER DE LA ENERGIA.',NOW()),
	 ('2APIA','LENGUA Y COMUNICACION II',NOW());
INSERT INTO group_course (group_id,course_name,created_at) VALUES
	 ('2APIA','CIENCIAS SOCIALES II',NOW()),
	 ('2APIA','CULTURA DIGITAL II',NOW()),
	 ('2APIA','RECURSO SOCIOEMOCIONAL II',NOW()),
	 ('2APIA','REALIZA ANALISIS FISICOS Y QUIMICOS A LA MATERIA PRIMA',NOW()),
	 ('2APIA','REALIZA ANALISIS MICROBIOLOGICOS A LA MATERIA PRIMA',NOW()),
	 ('2BPIA','PENSAMIENTO MATEMATICO II',NOW()),
	 ('2BPIA','INGLES II',NOW()),
	 ('2BPIA','CIENCIAS NATURALES, EXPERIMENTALES Y TECNOLOGIA II. EL PODER DE LA ENERGIA.',NOW()),
	 ('2BPIA','LENGUA Y COMUNICACION II',NOW()),
	 ('2BPIA','CIENCIAS SOCIALES II',NOW());
INSERT INTO group_course (group_id,course_name,created_at) VALUES
	 ('2BPIA','CULTURA DIGITAL II',NOW()),
	 ('2BPIA','RECURSO SOCIOEMOCIONAL II',NOW()),
	 ('2BPIA','REALIZA ANALISIS FISICOS Y QUIMICOS A LA MATERIA PRIMA',NOW()),
	 ('2BPIA','REALIZA ANALISIS MICROBIOLOGICOS A LA MATERIA PRIMA',NOW()),
	 ('2ATEM','PENSAMIENTO MATEMATICO II',NOW()),
	 ('2ATEM','INGLES II',NOW()),
	 ('2ATEM','CIENCIAS NATURALES, EXPERIMENTALES Y TECNOLOGIA II. EL PODER DE LA ENERGIA.',NOW()),
	 ('2ATEM','LENGUA Y COMUNICACION II',NOW()),
	 ('2ATEM','CIENCIAS SOCIALES II',NOW()),
	 ('2ATEM','CULTURA DIGITAL II',NOW());
INSERT INTO group_course (group_id,course_name,created_at) VALUES
	 ('2ATEM','RECURSO SOCIOEMOCIONAL II',NOW()),
	 ('2ATEM','DISEÑA PLANOS Y DIAGRAMAS ELECTRICOS Y ELECTRONICOS DE SISTEMAS ELECTROMECANICOS',NOW()),
	 ('2ATEM','REALIZA INSTALACIONES ELECTRICAS EN EQUIPOS ELECTROMECANICOS',NOW()),
	 ('2ATEM','REALIZA INSTALACIONES DE CIRCUITOS ELECTRONICOS EN SISTEMAS ELECTROMECANICOS',NOW()),
	 ('2BTEM','PENSAMIENTO MATEMATICO II',NOW()),
	 ('2BTEM','INGLES II',NOW()),
	 ('2BTEM','CIENCIAS NATURALES, EXPERIMENTALES Y TECNOLOGIA II. EL PODER DE LA ENERGIA.',NOW()),
	 ('2BTEM','LENGUA Y COMUNICACION II',NOW()),
	 ('2BTEM','CIENCIAS SOCIALES II',NOW()),
	 ('2BTEM','CULTURA DIGITAL II',NOW());
INSERT INTO group_course (group_id,course_name,created_at) VALUES
	 ('2BTEM','RECURSO SOCIOEMOCIONAL II',NOW()),
	 ('2BTEM','DISEÑA PLANOS Y DIAGRAMAS ELECTRICOS Y ELECTRONICOS DE SISTEMAS ELECTROMECANICOS',NOW()),
	 ('2BTEM','REALIZA INSTALACIONES ELECTRICAS EN EQUIPOS ELECTROMECANICOS',NOW()),
	 ('2BTEM','REALIZA INSTALACIONES DE CIRCUITOS ELECTRONICOS EN SISTEMAS ELECTROMECANICOS',NOW()),
	 ('2ACSG','PENSAMIENTO MATEMATICO II',NOW()),
	 ('2ACSG','INGLES II',NOW()),
	 ('2ACSG','CIENCIAS NATURALES, EXPERIMENTALES Y TECNOLOGIA II. EL PODER DE LA ENERGIA.',NOW()),
	 ('2ACSG','LENGUA Y COMUNICACION II',NOW()),
	 ('2ACSG','CIENCIAS SOCIALES II',NOW()),
	 ('2ACSG','CULTURA DIGITAL II',NOW());
INSERT INTO group_course (group_id,course_name,created_at) VALUES
	 ('2ACSG','RECURSO SOCIOEMOCIONAL II',NOW()),
	 ('2ACSG','DISEÑA ALGORITMOS DE PROBLEMAS DE SEGURIDAD',NOW()),
	 ('2ACSG','IMPLEMENTA SCRIPTS EN UN LENGUAJE DE PROGRAMACION PARA SOL DE PROB DE SEGURIDAD',NOW()),
	 ('2APRO','PENSAMIENTO MATEMATICO II',NOW()),
	 ('2APRO','INGLES II',NOW()),
	 ('2APRO','CIENCIAS NATURALES, EXPERIMENTALES Y TECNOLOGIA II. EL PODER DE LA ENERGIA.',NOW()),
	 ('2APRO','LENGUA Y COMUNICACION II',NOW()),
	 ('2APRO','CIENCIAS SOCIALES II',NOW()),
	 ('2APRO','CULTURA DIGITAL II',NOW()),
	 ('2APRO','RECURSO SOCIOEMOCIONAL II',NOW());
INSERT INTO group_course (group_id,course_name,created_at) VALUES
	 ('2APRO','DISEÑA SOFTWARE DE SISTEMAS INFORMATICOS',NOW()),
	 ('2APRO','CODIFICA SOFTWARE DE SISTEMAS INFORMATICOS',NOW()),
	 ('2APRO','IMPLEMENTA SOFTWARE DE SISTEMAS INFORMATICOS',NOW()),
	 ('2ATIA','PENSAMIENTO MATEMATICO II',NOW()),
	 ('2ATIA','INGLES II',NOW()),
	 ('2ATIA','CIENCIAS NATURALES, EXPERIMENTALES Y TECNOLOGIA II. EL PODER DE LA ENERGIA.',NOW()),
	 ('2ATIA','LENGUA Y COMUNICACION II',NOW()),
	 ('2ATIA','CIENCIAS SOCIALES II',NOW()),
	 ('2ATIA','CULTURA DIGITAL II',NOW()),
	 ('2ATIA','RECURSO SOCIOEMOCIONAL II',NOW());
INSERT INTO group_course (group_id,course_name,created_at) VALUES
	 ('2ATIA','DESARROLLA ALGORITMOS PARA SOLUCIONAR PROBLEMAS',NOW()),
	 ('2ATIA','ELABORA PROYECTOS CON PROGRAMACION LOGICA',NOW()),
	 ('4AARH','INGLES IV',NOW()),
	 ('4AARH','TEMAS SELECTOS DE MATEMATICAS I',NOW()),
	 ('4AARH','CONCIENCIA HISTORICA I',NOW()),
	 ('4AARH','REACCIONES QUIMICAS: CONSERVACION DE LA MATERIA EN LA FORMACION DE NUEVAS SUSTANCIAS',NOW()),
	 ('4AARH','CIENCIAS SOCIALES III',NOW()),
	 ('4AARH','RECURSOS SOCIOEMOCIONALES III',NOW()),
	 ('4AARH','TUTORIAS IV',NOW()),
	 ('4AARH','GESTIONA LOS PROCESOS DE CAPACITACION PARA EL DESARROLLO DEL TALENTO HUMANO',NOW());
INSERT INTO group_course (group_id,course_name,created_at) VALUES
	 ('4AARH','PROMUEVE CONDICIONES DE TRABAJO SALUDABLES EN LA ORGANIZACION',NOW()),
	 ('4APIA','INGLES IV',NOW()),
	 ('4APIA','TEMAS SELECTOS DE MATEMATICAS I',NOW()),
	 ('4APIA','CONCIENCIA HISTORICA I',NOW()),
	 ('4APIA','REACCIONES QUIMICAS: CONSERVACION DE LA MATERIA EN LA FORMACION DE NUEVAS SUSTANCIAS',NOW()),
	 ('4APIA','CIENCIAS SOCIALES III',NOW()),
	 ('4APIA','RECURSOS SOCIOEMOCIONALES III',NOW()),
	 ('4APIA','TUTORIAS IV',NOW()),
	 ('4APIA','REALIZA ANALISIS FISICOS, QUIMICOS Y MICROBIOLOGICOS EN CARNES Y SUS DERIVADOS',NOW()),
	 ('4APIA','TRANSFORMA CARNE Y SUS DERIVADOS EN PRODUCTOS ALIMENTICIOS',NOW());
INSERT INTO group_course (group_id,course_name,created_at) VALUES
	 ('4ATEM','INGLES IV',NOW()),
	 ('4ATEM','TEMAS SELECTOS DE MATEMATICAS I',NOW()),
	 ('4ATEM','CONCIENCIA HISTORICA I',NOW()),
	 ('4ATEM','REACCIONES QUIMICAS: CONSERVACION DE LA MATERIA EN LA FORMACION DE NUEVAS SUSTANCIAS',NOW()),
	 ('4ATEM','CIENCIAS SOCIALES III',NOW()),
	 ('4ATEM','RECURSOS SOCIOEMOCIONALES III',NOW()),
	 ('4ATEM','TUTORIAS IV',NOW()),
	 ('4ATEM','MAQUINA PIEZAS MECANICAS EN TORNO Y FRESADORA CONVENCIONAL',NOW()),
	 ('4ATEM','MAQUINA PIEZAS MECANICAS EN TORNO Y FRESADORA CNC',NOW()),
	 ('4ATEM','CONSTRUYE ESTRUCTURAS METALICAS PARA LA INDUSTRIA',NOW());
INSERT INTO group_course (group_id,course_name,created_at) VALUES
	 ('4ATEC','INGLES IV',NOW()),
	 ('4ATEC','TEMAS SELECTOS DE MATEMATICAS I',NOW()),
	 ('4ATEC','CONCIENCIA HISTORICA I',NOW()),
	 ('4ATEC','REACCIONES QUIMICAS: CONSERVACION DE LA MATERIA EN LA FORMACION DE NUEVAS SUSTANCIAS',NOW()),
	 ('4ATEC','CIENCIAS SOCIALES III',NOW()),
	 ('4ATEC','RECURSOS SOCIOEMOCIONALES III',NOW()),
	 ('4ATEC','TUTORIAS IV',NOW()),
	 ('4ATEC','REALIZA MANTENIMIENTO A SISTEMAS ELECTRICOS DE POTENCIA',NOW()),
	 ('4ATEC','PROGRAMA PLC PARA SISTEMAS AUTOMATIZADOS',NOW()),
	 ('4APRO','INGLES IV',NOW());
INSERT INTO group_course (group_id,course_name,created_at) VALUES
	 ('4APRO','TEMAS SELECTOS DE MATEMATICAS I',NOW()),
	 ('4APRO','CONCIENCIA HISTORICA I',NOW()),
	 ('4APRO','REACCIONES QUIMICAS: CONSERVACION DE LA MATERIA EN LA FORMACION DE NUEVAS SUSTANCIAS',NOW()),
	 ('4APRO','CIENCIAS SOCIALES III',NOW()),
	 ('4APRO','RECURSOS SOCIOEMOCIONALES III',NOW()),
	 ('4APRO','TUTORIAS IV',NOW()),
	 ('4APRO','IMPLEMENTA BASE DE DATOS RELACIONALES EN UN SISTEMA DE INFORMACION',NOW()),
	 ('4APRO','IMPLEMENTA BASE DE DATOS NO RELACIONALES EN UN SISTEMA DE INFORMACION',NOW()),
	 ('4ATCS','INGLES IV',NOW()),
	 ('4ATCS','TEMAS SELECTOS DE MATEMATICAS I',NOW());
INSERT INTO group_course (group_id,course_name,created_at) VALUES
	 ('4ATCS','CONCIENCIA HISTORICA I',NOW()),
	 ('4ATCS','REACCIONES QUIMICAS: CONSERVACION DE LA MATERIA EN LA FORMACION DE NUEVAS SUSTANCIAS',NOW()),
	 ('4ATCS','CIENCIAS SOCIALES III',NOW()),
	 ('4ATCS','RECURSOS SOCIOEMOCIONALES III',NOW()),
	 ('4ATCS','TUTORIAS IV',NOW()),
	 ('4ATCS','DETECTA VULNERABILIDADES EN SISTEMAS INFORMATICOS',NOW()),
	 ('4ATCS','CORRIGE VULNERABILIDADES EN SISTEMAS INFORMATICOS',NOW()),
	 ('6ADRH','TEMAS SELECTOS DE MATEMATICAS III',NOW()),
	 ('6ADRH','CONCIENCIA HISTÓRICA. LA REALIDAD ACTUAL EN PERSPECTIVA HISTORICA.',NOW()),
	 ('6ADRH','HUMANIDADES III',NOW());
INSERT INTO group_course (group_id,course_name,created_at) VALUES
	 ('6ADRH','ORGANISMOS: ESTRUCTURAS Y PROCESOS. HERENCIA Y EVOLUCIÓN BIOLÓGICA',NOW()),
	 ('6ADRH','HUMANISMO Y PENSAMIENTO FILOSÓFICO EN MÉXICO',NOW()),
	 ('6ADRH','TUTORIAS III',NOW()),
	 ('6ADRH','AUXILIA EN EL CÁLCULO DE LA NOMINA ORDINARIA',NOW()),
	 ('6ADRH','AUXILIA EN EL CÁLCULO DE LA NOMINA EXTRAORDINARIA',NOW()),
	 ('6AIAL','TEMAS SELECTOS DE MATEMATICAS III',NOW()),
	 ('6AIAL','CONCIENCIA HISTÓRICA. LA REALIDAD ACTUAL EN PERSPECTIVA HISTORICA.',NOW()),
	 ('6AIAL','HUMANIDADES III',NOW()),
	 ('6AIAL','ORGANISMOS: ESTRUCTURAS Y PROCESOS. HERENCIA Y EVOLUCIÓN BIOLÓGICA',NOW());
INSERT INTO group_course (group_id,course_name,created_at) VALUES
	 ('6AIAL','INTERACCIONES HUMANAS CON LA NATURALEZA',NOW()),
	 ('6AIAL','TUTORIAS III',NOW()),
	 ('6AIAL','REALIZA LOS ANÁLISIS FÍSICOS, QUÍMICOS Y MICROBIOLÓGICOS DE LOS PRODUCTOS DE CEREALES U OLEAGINOSAS Y PRODUCTOS DERIVADOS',NOW()),
	 ('6AIAL','REALIZA LOS PROCESOS DE TRANSFORMACIÓN DE CEREALES Y PRODUCTOS DERIVADOS',NOW()),
	 ('6ATEM','TEMAS SELECTOS DE MATEMATICAS III',NOW()),
	 ('6ATEM','CONCIENCIA HISTÓRICA. LA REALIDAD ACTUAL EN PERSPECTIVA HISTORICA.',NOW()),
	 ('6ATEM','HUMANIDADES III',NOW()),
	 ('6ATEM','ORGANISMOS: ESTRUCTURAS Y PROCESOS. HERENCIA Y EVOLUCIÓN BIOLÓGICA',NOW());
INSERT INTO group_course (group_id,course_name,created_at) VALUES
	 ('6ATEM','INTERACCIONES HUMANAS CON LA NATURALEZA',NOW()),
	 ('6ATEM','TUTORIAS III',NOW()),
	 ('6ATEM','MANTIENE EQUIPOS HIDRAULICOS',NOW()),
	 ('6ATEM','MANTIENE EQUIPOS NEUMATICOS',NOW()),
	 ('6ATEM','MANTIENE EQUIPOS DE REFRIGERACION',NOW()),
	 ('6ATEC','TEMAS SELECTOS DE MATEMATICAS III',NOW()),
	 ('6ATEC','CONCIENCIA HISTÓRICA. LA REALIDAD ACTUAL EN PERSPECTIVA HISTORICA.',NOW()),
	 ('6ATEC','HUMANIDADES III',NOW()),
	 ('6ATEC','ORGANISMOS: ESTRUCTURAS Y PROCESOS. HERENCIA Y EVOLUCIÓN BIOLÓGICA',NOW());
INSERT INTO group_course (group_id,course_name,created_at) VALUES
	 ('6ATEC','INTERACCIONES HUMANAS CON LA NATURALEZA',NOW()),
	 ('6ATEC','TUTORIAS III',NOW()),
	 ('6ATEC','INSTALA SISTEMAS ELECTRONICOS DOMOTICOS',NOW()),
	 ('6ATEC','INSTALA SISTEMAS ELECTRONICOS INDUSTRIALES AUTOMATIZADOS',NOW()),
	 ('6APRG','TEMAS SELECTOS DE MATEMATICAS III',NOW()),
	 ('6APRG','CONCIENCIA HISTÓRICA. LA REALIDAD ACTUAL EN PERSPECTIVA HISTORICA.',NOW()),
	 ('6APRG','HUMANIDADES III',NOW()),
	 ('6APRG','ORGANISMOS: ESTRUCTURAS Y PROCESOS. HERENCIA Y EVOLUCIÓN BIOLÓGICA',NOW()),
	 ('6APRG','INTERACCIONES HUMANAS CON LA NATURALEZA',NOW());
INSERT INTO group_course (group_id,course_name,created_at) VALUES
	 ('6APRG','TUTORIAS III',NOW()),
	 ('6APRG','DISEÑA APLICACIONES MOVILES MULTIPLATAFORMA',NOW()),
	 ('6APRG','IMPLEMENTA APLICACIONES MOVILES MULTIPLATAFORMA',NOW());

commit;


-- ============================================================================
-- COURSE ASSIGNMENTS
-- ============================================================================
-- Generate course assignments for each group+course combination
-- Each assignment represents one hour of the course
-- For example, "PENSAMIENTO MATEMÁTICO I" requires 4 hours, so we create 4 assignments
-- with sequence_index 0, 1, 2, 3

-- Note: teacher_id, timeslot_id, and room_name are NULL initially
-- The Timefold solver will assign these values

-- Counter starts at 0 and increments for each assignment
INSERT INTO course_assignment (id, group_id, course_id, sequence_index, teacher_id, timeslot_id, room_name)
SELECT
    'assignment_' || ROW_NUMBER() OVER (ORDER BY g.id, c.id, seq.n) - 1 AS id,
    g.id AS group_id,
    c.id AS course_id,
    seq.n AS sequence_index,
    NULL AS teacher_id,
    NULL AS timeslot_id,
    NULL AS room_name
FROM student_group g
INNER JOIN group_course gc ON g.id = gc.group_id
INNER JOIN course c ON gc.course_name = c.name
CROSS JOIN LATERAL generate_series(0, c.required_hours_per_week - 1) AS seq(n)
ORDER BY g.id, c.id, seq.n;

-- ------------------
-- COURSE ASSIGNMENTS
-- ------------------
-- COURSE ASSIGNMENTS FOR SEMESTRE II
UPDATE course_assignment SET teacher_id='48GMMM', room_name='SALON 7', timeslot_id=NULL, updated_at=NOW() WHERE course_id='4' AND group_id='2AARH'; 
UPDATE course_assignment SET teacher_id='48GMMM', room_name='SALON 5', timeslot_id=NULL, updated_at=NOW() WHERE course_id='4' AND group_id='2ACIA';  
UPDATE course_assignment SET teacher_id='48GMMM', room_name='SALON 6', timeslot_id=NULL, updated_at=NOW() WHERE course_id='4' AND group_id='2APIA';  
UPDATE course_assignment SET teacher_id='48GMMM', room_name='SALON 8', timeslot_id=NULL, updated_at=NOW() WHERE course_id='4' AND group_id='2BPIA';  
UPDATE course_assignment SET teacher_id='45EUSAS', room_name='SALON 9', timeslot_id=NULL, updated_at=NOW() WHERE course_id='4' AND group_id='2ATEM';  
UPDATE course_assignment SET teacher_id='45EUSAS', room_name='SALON 10', timeslot_id=NULL, updated_at=NOW() WHERE course_id='4' AND group_id='2BTEM';  
UPDATE course_assignment SET teacher_id='48GMMM', room_name='SALON 11', timeslot_id=NULL, updated_at=NOW() WHERE course_id='4' AND group_id='2ACSG';  
UPDATE course_assignment SET teacher_id='48GMMM', room_name='SALON 12', timeslot_id=NULL, updated_at=NOW() WHERE course_id='4' AND group_id='2APRO';  
UPDATE course_assignment SET teacher_id='48GMMM', room_name='SALON 13', timeslot_id=NULL, updated_at=NOW() WHERE course_id='4' AND group_id='2ATIA';  
UPDATE course_assignment SET teacher_id='48JAABQ', room_name='SALON 7', timeslot_id=NULL, updated_at=NOW() WHERE course_id='1' AND group_id='2AARH';  
UPDATE course_assignment SET teacher_id='48DRLSO', room_name='SALON 2', timeslot_id=NULL, updated_at=NOW() WHERE course_id='1' AND group_id='2ACIA';  
UPDATE course_assignment SET teacher_id='48DRLSO', room_name='SALON 2', timeslot_id=NULL, updated_at=NOW() WHERE course_id='1' AND group_id='2APIA';  
UPDATE course_assignment SET teacher_id='48JAABQ', room_name='SALON 8', timeslot_id=NULL, updated_at=NOW() WHERE course_id='1' AND group_id='2BPIA';  
UPDATE course_assignment SET teacher_id='48LSCS', room_name='SALON 9', timeslot_id=NULL, updated_at=NOW() WHERE course_id='1' AND group_id='2ATEM';  
UPDATE course_assignment SET teacher_id='48LSCS', room_name='SALON 10', timeslot_id=NULL, updated_at=NOW() WHERE course_id='1' AND group_id='2BTEM';  
UPDATE course_assignment SET teacher_id='48HGRO', room_name='SALON 11', timeslot_id=NULL, updated_at=NOW() WHERE course_id='1' AND group_id='2ACSG';  
UPDATE course_assignment SET teacher_id='48LSCS', room_name='SALON 12', timeslot_id=NULL, updated_at=NOW() WHERE course_id='1' AND group_id='2APRO';  
UPDATE course_assignment SET teacher_id='48JAABQ', room_name='SALON 13', timeslot_id=NULL, updated_at=NOW() WHERE course_id='1' AND group_id='2ATIA';  
UPDATE course_assignment SET teacher_id='48YHGN', room_name='SALON 7', timeslot_id=NULL, updated_at=NOW() WHERE course_id='3' AND group_id='2AARH';  
UPDATE course_assignment SET teacher_id='48ASOG', room_name='SALON 5', timeslot_id=NULL, updated_at=NOW() WHERE course_id='3' AND group_id='2ACIA';  
UPDATE course_assignment SET teacher_id='48ASOG', room_name='SALON 6', timeslot_id=NULL, updated_at=NOW() WHERE course_id='3' AND group_id='2APIA';  
UPDATE course_assignment SET teacher_id='48ASOG', room_name='SALON 8', timeslot_id=NULL, updated_at=NOW() WHERE course_id='3' AND group_id='2BPIA';  
UPDATE course_assignment SET teacher_id='48ASOE', room_name='SALON 9', timeslot_id=NULL, updated_at=NOW() WHERE course_id='3' AND group_id='2ATEM';  
UPDATE course_assignment SET teacher_id='48ASOE', room_name='SALON 10', timeslot_id=NULL, updated_at=NOW() WHERE course_id='3' AND group_id='2BTEM';  
UPDATE course_assignment SET teacher_id='48ASOG', room_name='SALON 11', timeslot_id=NULL, updated_at=NOW() WHERE course_id='3' AND group_id='2ACSG';  
UPDATE course_assignment SET teacher_id='48ASOE', room_name='SALON 12', timeslot_id=NULL, updated_at=NOW() WHERE course_id='3' AND group_id='2APRO';  
UPDATE course_assignment SET teacher_id='48ASOG', room_name='SALON 13', timeslot_id=NULL, updated_at=NOW() WHERE course_id='3' AND group_id='2ATIA';  
UPDATE course_assignment SET teacher_id='48MAGCE', room_name='SALON 7', timeslot_id=NULL, updated_at=NOW() WHERE course_id='5' AND group_id='2AARH';  
UPDATE course_assignment SET teacher_id='48MAGCE', room_name='SALON 5', timeslot_id=NULL, updated_at=NOW() WHERE course_id='5' AND group_id='2ACIA';  
UPDATE course_assignment SET teacher_id='48MAGCE', room_name='SALON 6', timeslot_id=NULL, updated_at=NOW() WHERE course_id='5' AND group_id='2APIA';  
UPDATE course_assignment SET teacher_id='48MAGCE', room_name='SALON 8', timeslot_id=NULL, updated_at=NOW() WHERE course_id='5' AND group_id='2BPIA';  
UPDATE course_assignment SET teacher_id='48MAGCE', room_name='SALON 9', timeslot_id=NULL, updated_at=NOW() WHERE course_id='5' AND group_id='2ATEM';  
UPDATE course_assignment SET teacher_id='48MAGCE', room_name='SALON 10', timeslot_id=NULL, updated_at=NOW() WHERE course_id='5' AND group_id='2BTEM';  
UPDATE course_assignment SET teacher_id='47LMMB', room_name='SALON 11', timeslot_id=NULL, updated_at=NOW() WHERE course_id='5' AND group_id='2ACSG';  
UPDATE course_assignment SET teacher_id='47YAMSC', room_name='SALON 12', timeslot_id=NULL, updated_at=NOW() WHERE course_id='5' AND group_id='2APRO';  
UPDATE course_assignment SET teacher_id='47LMMB', room_name='SALON 13', timeslot_id=NULL, updated_at=NOW() WHERE course_id='5' AND group_id='2ATIA';  
UPDATE course_assignment SET teacher_id='48ISOT', room_name='SALON 7', timeslot_id=NULL, updated_at=NOW() WHERE course_id='6' AND group_id='2AARH';  
UPDATE course_assignment SET teacher_id='46JCRCY', room_name='SALON 5', timeslot_id=NULL, updated_at=NOW() WHERE course_id='6' AND group_id='2ACIA';  
UPDATE course_assignment SET teacher_id='48ISOT', room_name='SALON 6', timeslot_id=NULL, updated_at=NOW() WHERE course_id='6' AND group_id='2APIA';  
UPDATE course_assignment SET teacher_id='48ISOT', room_name='SALON 8', timeslot_id=NULL, updated_at=NOW() WHERE course_id='6' AND group_id='2BPIA';  
UPDATE course_assignment SET teacher_id='48ISOT', room_name='SALON 9', timeslot_id=NULL, updated_at=NOW() WHERE course_id='6' AND group_id='2ATEM';  
UPDATE course_assignment SET teacher_id='48ISOT', room_name='SALON 10', timeslot_id=NULL, updated_at=NOW() WHERE course_id='6' AND group_id='2BTEM';  
UPDATE course_assignment SET teacher_id='45MVVT', room_name='SALON 11', timeslot_id=NULL, updated_at=NOW() WHERE course_id='6' AND group_id='2ACSG';  
UPDATE course_assignment SET teacher_id='45MVVT', room_name='SALON 12', timeslot_id=NULL, updated_at=NOW() WHERE course_id='6' AND group_id='2APRO';  
UPDATE course_assignment SET teacher_id='45MVVT', room_name='SALON 13', timeslot_id=NULL, updated_at=NOW() WHERE course_id='6' AND group_id='2ATIA';  
UPDATE course_assignment SET teacher_id='48JACAJ', room_name='SALON 7', timeslot_id=NULL, updated_at=NOW() WHERE course_id='2' AND group_id='2AARH';  
UPDATE course_assignment SET teacher_id='46REJA', room_name='SALON 5', timeslot_id=NULL, updated_at=NOW() WHERE course_id='2' AND group_id='2ACIA';  
UPDATE course_assignment SET teacher_id='48JACAJ', room_name='SALON 6', timeslot_id=NULL, updated_at=NOW() WHERE course_id='2' AND group_id='2APIA';  
UPDATE course_assignment SET teacher_id='48JACAJ', room_name='SALON 8', timeslot_id=NULL, updated_at=NOW() WHERE course_id='2' AND group_id='2BPIA';  
UPDATE course_assignment SET teacher_id='46REJA', room_name='SALON 9', timeslot_id=NULL, updated_at=NOW() WHERE course_id='2' AND group_id='2ATEM';  
UPDATE course_assignment SET teacher_id='46REJA', room_name='SALON 10', timeslot_id=NULL, updated_at=NOW() WHERE course_id='2' AND group_id='2BTEM';  
UPDATE course_assignment SET teacher_id='46REJA', room_name='SALON 11', timeslot_id=NULL, updated_at=NOW() WHERE course_id='2' AND group_id='2ACSG';  
UPDATE course_assignment SET teacher_id='46REJA', room_name='SALON 12', timeslot_id=NULL, updated_at=NOW() WHERE course_id='2' AND group_id='2APRO';  
UPDATE course_assignment SET teacher_id='46REJA', room_name='SALON 13', timeslot_id=NULL, updated_at=NOW() WHERE course_id='2' AND group_id='2ATIA';  
UPDATE course_assignment SET teacher_id='48ABCJ', room_name='SALON 7', timeslot_id=NULL, updated_at=NOW() WHERE course_id='7' AND group_id='2AARH';  
UPDATE course_assignment SET teacher_id='48MAGCE', room_name='SALON 5', timeslot_id=NULL, updated_at=NOW() WHERE course_id='7' AND group_id='2ACIA';  
UPDATE course_assignment SET teacher_id='46IJCAI', room_name='SALON 6', timeslot_id=NULL, updated_at=NOW() WHERE course_id='7' AND group_id='2APIA';  
UPDATE course_assignment SET teacher_id='46IJCAI', room_name='SALON 8', timeslot_id=NULL, updated_at=NOW() WHERE course_id='7' AND group_id='2BPIA';  
UPDATE course_assignment SET teacher_id='48PBRAU', room_name='SALON 9', timeslot_id=NULL, updated_at=NOW() WHERE course_id='7' AND group_id='2ATEM';  
UPDATE course_assignment SET teacher_id='48PBRAU', room_name='SALON 10', timeslot_id=NULL, updated_at=NOW() WHERE course_id='7' AND group_id='2BTEM';  
UPDATE course_assignment SET teacher_id='46LDNRS', room_name='SALON 11', timeslot_id=NULL, updated_at=NOW() WHERE course_id='7' AND group_id='2ACSG';  
UPDATE course_assignment SET teacher_id='48JBWT', room_name='SALON 12', timeslot_id=NULL, updated_at=NOW() WHERE course_id='7' AND group_id='2APRO';  
UPDATE course_assignment SET teacher_id='48LFGD', room_name='SALON 13', timeslot_id=NULL, updated_at=NOW() WHERE course_id='7' AND group_id='2ATIA';  
UPDATE course_assignment SET teacher_id='47SVPE', room_name='SALON 7', timeslot_id=NULL, updated_at=NOW() WHERE course_id='8' AND group_id='2AARH';  
UPDATE course_assignment SET teacher_id='47SVPE', room_name='SALON 7', timeslot_id=NULL, updated_at=NOW() WHERE course_id='9' AND group_id='2AARH';  
UPDATE course_assignment SET teacher_id='47FRSO', room_name='SALON 5', timeslot_id=NULL, updated_at=NOW() WHERE course_id='13' AND group_id='2ACIA';  
UPDATE course_assignment SET teacher_id='48ABCJ', room_name='SALON 5', timeslot_id=NULL, updated_at=NOW() WHERE course_id='14' AND group_id='2ACIA';  
UPDATE course_assignment SET teacher_id='48SLAMC', room_name='SALON 6', timeslot_id=NULL, updated_at=NOW() WHERE course_id='15' AND group_id='2APIA';  
UPDATE course_assignment SET teacher_id='48YESMR', room_name='SALON 8', timeslot_id=NULL, updated_at=NOW() WHERE course_id='15' AND group_id='2BPIA';  
UPDATE course_assignment SET teacher_id='48SLAMC', room_name='SALON 4', timeslot_id=NULL, updated_at=NOW() WHERE course_id='16' AND group_id='2APIA';  
UPDATE course_assignment SET teacher_id='48YESMR', room_name='SALON 4', timeslot_id=NULL, updated_at=NOW() WHERE course_id='16' AND group_id='2BPIA';  
UPDATE course_assignment SET teacher_id='46MASOC', room_name='TEM 1', timeslot_id=NULL, updated_at=NOW() WHERE course_id='10' AND group_id='2ATEM';  
UPDATE course_assignment SET teacher_id='46MASOC', room_name='TEM 1', timeslot_id=NULL, updated_at=NOW() WHERE course_id='10' AND group_id='2BTEM';  
UPDATE course_assignment SET teacher_id='48PBRAU', room_name='TEM 2', timeslot_id=NULL, updated_at=NOW() WHERE course_id='11' AND group_id='2ATEM';  
UPDATE course_assignment SET teacher_id='48PBRAU', room_name='TEM 2', timeslot_id=NULL, updated_at=NOW() WHERE course_id='11' AND group_id='2BTEM';  
UPDATE course_assignment SET teacher_id='47CGHV', room_name='TEM 3', timeslot_id=NULL, updated_at=NOW() WHERE course_id='12' AND group_id='2ATEM';  
UPDATE course_assignment SET teacher_id='47CGHV', room_name='TEM 3', timeslot_id=NULL, updated_at=NOW() WHERE course_id='12' AND group_id='2BTEM';  
UPDATE course_assignment SET teacher_id='48ISOT', room_name='SALON 11', timeslot_id=NULL, updated_at=NOW() WHERE course_id='17' AND group_id='2ACSG';  
UPDATE course_assignment SET teacher_id='48HGRO', room_name='CC 3', timeslot_id=NULL, updated_at=NOW() WHERE course_id='18' AND group_id='2ACSG';  
UPDATE course_assignment SET teacher_id='48LFGD', room_name='CC 1', timeslot_id=NULL, updated_at=NOW() WHERE course_id='19' AND group_id='2APRO';  
UPDATE course_assignment SET teacher_id='48LFGD', room_name='CC 1', timeslot_id=NULL, updated_at=NOW() WHERE course_id='20' AND group_id='2APRO';  
UPDATE course_assignment SET teacher_id='48LFGD', room_name='CC 1', timeslot_id=NULL, updated_at=NOW() WHERE course_id='21' AND group_id='2APRO';  
UPDATE course_assignment SET teacher_id='45MVVT', room_name='CC 2', timeslot_id=NULL, updated_at=NOW() WHERE course_id='22' AND group_id='2ATIA';  
UPDATE course_assignment SET teacher_id='48ERNE', room_name='CC 2', timeslot_id=NULL, updated_at=NOW() WHERE course_id='23' AND group_id='2ATIA';  
-- COURSE ASSIGNMENTS FOR SEMESTRE IV
UPDATE course_assignment SET teacher_id='46YVVGG', room_name='SALON 14', timeslot_id=NULL, updated_at=NOW() WHERE course_id='25' AND group_id='4AARH';  
UPDATE course_assignment SET teacher_id='46YVVGG', room_name='SALON 15', timeslot_id=NULL, updated_at=NOW() WHERE course_id='25' AND group_id='4APIA';  
UPDATE course_assignment SET teacher_id='46YVVGG', room_name='SALON 16', timeslot_id=NULL, updated_at=NOW() WHERE course_id='25' AND group_id='4ATEM';  
UPDATE course_assignment SET teacher_id='46YVVGG', room_name='SALON 17', timeslot_id=NULL, updated_at=NOW() WHERE course_id='25' AND group_id='4ATEC';  
UPDATE course_assignment SET teacher_id='46YVVGG', room_name='SALON 18', timeslot_id=NULL, updated_at=NOW() WHERE course_id='25' AND group_id='4APRO';  
UPDATE course_assignment SET teacher_id='46YVVGG', room_name='SALON 19', timeslot_id=NULL, updated_at=NOW() WHERE course_id='25' AND group_id='4ATCS';  
UPDATE course_assignment SET teacher_id='46ICJM', room_name='SALON 14', timeslot_id=NULL, updated_at=NOW() WHERE course_id='24' AND group_id='4AARH';  
UPDATE course_assignment SET teacher_id='48LSCS', room_name='SALON 15', timeslot_id=NULL, updated_at=NOW() WHERE course_id='24' AND group_id='4APIA';  
UPDATE course_assignment SET teacher_id='48AJAP', room_name='SALON 16', timeslot_id=NULL, updated_at=NOW() WHERE course_id='24' AND group_id='4ATEM';  
UPDATE course_assignment SET teacher_id='46ICJM', room_name='SALON 17', timeslot_id=NULL, updated_at=NOW() WHERE course_id='24' AND group_id='4ATEC';  
UPDATE course_assignment SET teacher_id='46ICJM', room_name='SALON 18', timeslot_id=NULL, updated_at=NOW() WHERE course_id='24' AND group_id='4APRO';  
UPDATE course_assignment SET teacher_id='46ICJM', room_name='SALON 19', timeslot_id=NULL, updated_at=NOW() WHERE course_id='24' AND group_id='4ATCS';  
UPDATE course_assignment SET teacher_id='48ASMO', room_name='SALON 1', timeslot_id=NULL, updated_at=NOW() WHERE course_id='26' AND group_id='4AARH';  
UPDATE course_assignment SET teacher_id='48ASMO', room_name='SALON 1', timeslot_id=NULL, updated_at=NOW() WHERE course_id='26' AND group_id='4APIA';  
UPDATE course_assignment SET teacher_id='48ASMO', room_name='SALON 1', timeslot_id=NULL, updated_at=NOW() WHERE course_id='26' AND group_id='4ATEM';  
UPDATE course_assignment SET teacher_id='45LDLSRP', room_name='SALON 17', timeslot_id=NULL, updated_at=NOW() WHERE course_id='26' AND group_id='4ATEC';  
UPDATE course_assignment SET teacher_id='48ASMO', room_name='SALON 1', timeslot_id=NULL, updated_at=NOW() WHERE course_id='26' AND group_id='4APRO';  
UPDATE course_assignment SET teacher_id='45LDLSRP', room_name='SALON 19', timeslot_id=NULL, updated_at=NOW() WHERE course_id='26' AND group_id='4ATCS';  
UPDATE course_assignment SET teacher_id='46LDNRS', room_name='SALON 14', timeslot_id=NULL, updated_at=NOW() WHERE course_id='27' AND group_id='4AARH';  
UPDATE course_assignment SET teacher_id='46LDNRS', room_name='SALON 15', timeslot_id=NULL, updated_at=NOW() WHERE course_id='27' AND group_id='4APIA';  
UPDATE course_assignment SET teacher_id='46LDNRS', room_name='SALON 16', timeslot_id=NULL, updated_at=NOW() WHERE course_id='27' AND group_id='4ATEM';  
UPDATE course_assignment SET teacher_id='48DLAOV', room_name='SALON 17', timeslot_id=NULL, updated_at=NOW() WHERE course_id='27' AND group_id='4ATEC';  
UPDATE course_assignment SET teacher_id='48DLAOV', room_name='SALON 18', timeslot_id=NULL, updated_at=NOW() WHERE course_id='27' AND group_id='4APRO';  
UPDATE course_assignment SET teacher_id='48DLAOV', room_name='SALON 19', timeslot_id=NULL, updated_at=NOW() WHERE course_id='27' AND group_id='4ATCS';  
UPDATE course_assignment SET teacher_id='48MAGCE', room_name='SALON 14', timeslot_id=NULL, updated_at=NOW() WHERE course_id='28' AND group_id='4AARH';  
UPDATE course_assignment SET teacher_id='48MAGCE', room_name='SALON 15', timeslot_id=NULL, updated_at=NOW() WHERE course_id='28' AND group_id='4APIA';  
UPDATE course_assignment SET teacher_id='48MAGCE', room_name='SALON 16', timeslot_id=NULL, updated_at=NOW() WHERE course_id='28' AND group_id='4ATEM';  
UPDATE course_assignment SET teacher_id='48MAGCE', room_name='SALON 17', timeslot_id=NULL, updated_at=NOW() WHERE course_id='28' AND group_id='4ATEC';  
UPDATE course_assignment SET teacher_id='48MAGCE', room_name='SALON 18', timeslot_id=NULL, updated_at=NOW() WHERE course_id='28' AND group_id='4APRO';  
UPDATE course_assignment SET teacher_id='48MAGCE', room_name='SALON 19', timeslot_id=NULL, updated_at=NOW() WHERE course_id='28' AND group_id='4ATCS';  
UPDATE course_assignment SET teacher_id='47SVPE', room_name='SALON 14', timeslot_id=NULL, updated_at=NOW() WHERE course_id='29' AND group_id='4AARH';  
UPDATE course_assignment SET teacher_id='46IJCAI', room_name='SALON 15', timeslot_id=NULL, updated_at=NOW() WHERE course_id='29' AND group_id='4APIA';  
UPDATE course_assignment SET teacher_id='48RRGB', room_name='SALON 16', timeslot_id=NULL, updated_at=NOW() WHERE course_id='29' AND group_id='4ATEM';  
UPDATE course_assignment SET teacher_id='46IJCAI', room_name='SALON 17', timeslot_id=NULL, updated_at=NOW() WHERE course_id='29' AND group_id='4ATEC';  
UPDATE course_assignment SET teacher_id='48ERNE', room_name='SALON 18', timeslot_id=NULL, updated_at=NOW() WHERE course_id='29' AND group_id='4APRO';  
UPDATE course_assignment SET teacher_id='47LMMB', room_name='SALON 19', timeslot_id=NULL, updated_at=NOW() WHERE course_id='29' AND group_id='4ATCS';  
UPDATE course_assignment SET teacher_id='47SVPE', room_name='SALON 14', timeslot_id=NULL, updated_at=NOW() WHERE course_id='30' AND group_id='4AARH';  
UPDATE course_assignment SET teacher_id='48YESMR', room_name='SALON 15', timeslot_id=NULL, updated_at=NOW() WHERE course_id='30' AND group_id='4APIA';  
UPDATE course_assignment SET teacher_id='48RRGB', room_name='SALON 16', timeslot_id=NULL, updated_at=NOW() WHERE course_id='30' AND group_id='4ATEM';  
UPDATE course_assignment SET teacher_id='48HGRO', room_name='SALON 17', timeslot_id=NULL, updated_at=NOW() WHERE course_id='30' AND group_id='4ATEC';  
UPDATE course_assignment SET teacher_id='48ERNE', room_name='SALON 18', timeslot_id=NULL, updated_at=NOW() WHERE course_id='30' AND group_id='4APRO';  
UPDATE course_assignment SET teacher_id='47LMMB', room_name='SALON 19', timeslot_id=NULL, updated_at=NOW() WHERE course_id='30' AND group_id='4ATCS';  
UPDATE course_assignment SET teacher_id='47SVPE', room_name='SALON 14', timeslot_id=NULL, updated_at=NOW() WHERE course_id='31' AND group_id='4AARH';  
UPDATE course_assignment SET teacher_id='48ABCJ', room_name='SALON 14', timeslot_id=NULL, updated_at=NOW() WHERE course_id='32' AND group_id='4AARH';  
UPDATE course_assignment SET teacher_id='46IUAW', room_name='SALON 4', timeslot_id=NULL, updated_at=NOW() WHERE course_id='33' AND group_id='4APIA';  
UPDATE course_assignment SET teacher_id='48YESMR', room_name='SALON 4', timeslot_id=NULL, updated_at=NOW() WHERE course_id='34' AND group_id='4APIA';  
UPDATE course_assignment SET teacher_id='48RRGB', room_name='TEM 1', timeslot_id=NULL, updated_at=NOW() WHERE course_id='35' AND group_id='4ATEM';  
UPDATE course_assignment SET teacher_id='48RRGB', room_name='TEM 2', timeslot_id=NULL, updated_at=NOW() WHERE course_id='36' AND group_id='4ATEM';  
UPDATE course_assignment SET teacher_id='47CGHV', room_name='TEM 3', timeslot_id=NULL, updated_at=NOW() WHERE course_id='37' AND group_id='4ATEM';  
UPDATE course_assignment SET teacher_id='46FNDZG', room_name='TE 1', timeslot_id=NULL, updated_at=NOW() WHERE course_id='38' AND group_id='4ATEC';  
UPDATE course_assignment SET teacher_id='48BCCS', room_name='TE 1', timeslot_id=NULL, updated_at=NOW() WHERE course_id='39' AND group_id='4ATEC';  
UPDATE course_assignment SET teacher_id='48ERNE', room_name='CC 2', timeslot_id=NULL, updated_at=NOW() WHERE course_id='40' AND group_id='4APRO';  
UPDATE course_assignment SET teacher_id='48ERNE', room_name='CC 2', timeslot_id=NULL, updated_at=NOW() WHERE course_id='41' AND group_id='4APRO';  
UPDATE course_assignment SET teacher_id='47LMMB', room_name='CC 3', timeslot_id=NULL, updated_at=NOW() WHERE course_id='42' AND group_id='4ATCS';  
UPDATE course_assignment SET teacher_id='48HGRO', room_name='CC 1', timeslot_id=NULL, updated_at=NOW() WHERE course_id='43' AND group_id='4ATCS';  
-- COURSE ASSIGNMENTS FOR SEMESTRE VI
UPDATE course_assignment SET teacher_id='48DRLSO', room_name='SALON 2', timeslot_id=NULL, updated_at=NOW() WHERE course_id='44' AND group_id='6ADRH';  
UPDATE course_assignment SET teacher_id='48DRLSO', room_name='SALON 2', timeslot_id=NULL, updated_at=NOW() WHERE course_id='44' AND group_id='6AIAL';  
UPDATE course_assignment SET teacher_id='48AJAP', room_name='SALON 22', timeslot_id=NULL, updated_at=NOW() WHERE course_id='44' AND group_id='6ATEM';  
UPDATE course_assignment SET teacher_id='48AJAP', room_name='SALON 17', timeslot_id=NULL, updated_at=NOW() WHERE course_id='44' AND group_id='6ATEC';  
UPDATE course_assignment SET teacher_id='48AJAP', room_name='SALON 23', timeslot_id=NULL, updated_at=NOW() WHERE course_id='44' AND group_id='6APRG';  
UPDATE course_assignment SET teacher_id='48ASMO', room_name='SALON 1', timeslot_id=NULL, updated_at=NOW() WHERE course_id='45' AND group_id='6ADRH';  
UPDATE course_assignment SET teacher_id='48ASMO', room_name='SALON 1', timeslot_id=NULL, updated_at=NOW() WHERE course_id='45' AND group_id='6AIAL';  
UPDATE course_assignment SET teacher_id='48ASMO', room_name='SALON 1', timeslot_id=NULL, updated_at=NOW() WHERE course_id='45' AND group_id='6ATEM';  
UPDATE course_assignment SET teacher_id='48ASMO', room_name='SALON 1', timeslot_id=NULL, updated_at=NOW() WHERE course_id='45' AND group_id='6ATEC';  
UPDATE course_assignment SET teacher_id='48ASMO', room_name='SALON 1', timeslot_id=NULL, updated_at=NOW() WHERE course_id='45' AND group_id='6APRG';  
UPDATE course_assignment SET teacher_id='48JBWT', room_name='SALON 20', timeslot_id=NULL, updated_at=NOW() WHERE course_id='46' AND group_id='6ADRH';  
UPDATE course_assignment SET teacher_id='48JBWT', room_name='SALON 21', timeslot_id=NULL, updated_at=NOW() WHERE course_id='46' AND group_id='6AIAL';  
UPDATE course_assignment SET teacher_id='48JBWT', room_name='SALON 22', timeslot_id=NULL, updated_at=NOW() WHERE course_id='46' AND group_id='6ATEM';  
UPDATE course_assignment SET teacher_id='48JBWT', room_name='SALON 17', timeslot_id=NULL, updated_at=NOW() WHERE course_id='46' AND group_id='6ATEC';  
UPDATE course_assignment SET teacher_id='48JBWT', room_name='SALON 23', timeslot_id=NULL, updated_at=NOW() WHERE course_id='46' AND group_id='6APRG';  
UPDATE course_assignment SET teacher_id='48JACAJ', room_name='SALON 20', timeslot_id=NULL, updated_at=NOW() WHERE course_id='47' AND group_id='6ADRH';  
UPDATE course_assignment SET teacher_id='48JACAJ', room_name='SALON 21', timeslot_id=NULL, updated_at=NOW() WHERE course_id='47' AND group_id='6AIAL';  
UPDATE course_assignment SET teacher_id='48JACAJ', room_name='SALON 22', timeslot_id=NULL, updated_at=NOW() WHERE course_id='47' AND group_id='6ATEM';  
UPDATE course_assignment SET teacher_id='48ASOE', room_name='SALON 17', timeslot_id=NULL, updated_at=NOW() WHERE course_id='47' AND group_id='6ATEC';  
UPDATE course_assignment SET teacher_id='48ASOE', room_name='SALON 23', timeslot_id=NULL, updated_at=NOW() WHERE course_id='47' AND group_id='6APRG';  
UPDATE course_assignment SET teacher_id='45LDLSRP', room_name='SALON 20', timeslot_id=NULL, updated_at=NOW() WHERE course_id='48' AND group_id='6ADRH';  
UPDATE course_assignment SET teacher_id='46IUAW', room_name='SALON 21', timeslot_id=NULL, updated_at=NOW() WHERE course_id='49' AND group_id='6AIAL';  
UPDATE course_assignment SET teacher_id='46LDNRS', room_name='SALON 22', timeslot_id=NULL, updated_at=NOW() WHERE course_id='49' AND group_id='6ATEM';  
UPDATE course_assignment SET teacher_id='48DLAOV', room_name='SALON 17', timeslot_id=NULL, updated_at=NOW() WHERE course_id='49' AND group_id='6ATEC';  
UPDATE course_assignment SET teacher_id='48ASOE', room_name='SALON 23', timeslot_id=NULL, updated_at=NOW() WHERE course_id='49' AND group_id='6APRG';  
UPDATE course_assignment SET teacher_id='47FRSO', room_name='SALON 20', timeslot_id=NULL, updated_at=NOW() WHERE course_id='50' AND group_id='6ADRH';  
UPDATE course_assignment SET teacher_id='48SLAMC', room_name='SALON 21', timeslot_id=NULL, updated_at=NOW() WHERE course_id='50' AND group_id='6AIAL';  
UPDATE course_assignment SET teacher_id='47CGHV', room_name='SALON 22', timeslot_id=NULL, updated_at=NOW() WHERE course_id='50' AND group_id='6ATEM';  
UPDATE course_assignment SET teacher_id='48HGRO', room_name='SALON 17', timeslot_id=NULL, updated_at=NOW() WHERE course_id='50' AND group_id='6ATEC';  
UPDATE course_assignment SET teacher_id='48ISOT', room_name='SALON 23', timeslot_id=NULL, updated_at=NOW() WHERE course_id='50' AND group_id='6APRG';  
UPDATE course_assignment SET teacher_id='48ABCJ', room_name='SALON 20', timeslot_id=NULL, updated_at=NOW() WHERE course_id='51' AND group_id='6ADRH';  
UPDATE course_assignment SET teacher_id='48ABCJ', room_name='SALON 20', timeslot_id=NULL, updated_at=NOW() WHERE course_id='52' AND group_id='6ADRH';  
UPDATE course_assignment SET teacher_id='46IUAW', room_name='SALON 4', timeslot_id=NULL, updated_at=NOW() WHERE course_id='53' AND group_id='6AIAL';  
UPDATE course_assignment SET teacher_id='48YESMR', room_name='SALON 4', timeslot_id=NULL, updated_at=NOW() WHERE course_id='54' AND group_id='6AIAL';  
UPDATE course_assignment SET teacher_id='48RRGB', room_name='TEM 1', timeslot_id=NULL, updated_at=NOW() WHERE course_id='55' AND group_id='6ATEM';  
UPDATE course_assignment SET teacher_id='48RRGB', room_name='TEM 2', timeslot_id=NULL, updated_at=NOW() WHERE course_id='56' AND group_id='6ATEM';  
UPDATE course_assignment SET teacher_id='46IJCAI', room_name='TEM 3', timeslot_id=NULL, updated_at=NOW() WHERE course_id='57' AND group_id='6ATEM';  
UPDATE course_assignment SET teacher_id='48HGRO', room_name='TE 1', timeslot_id=NULL, updated_at=NOW() WHERE course_id='58' AND group_id='6ATEC';  
UPDATE course_assignment SET teacher_id='46FNDZG', room_name='TE 1', timeslot_id=NULL, updated_at=NOW() WHERE course_id='59' AND group_id='6ATEC';  
UPDATE course_assignment SET teacher_id='48ISOT', room_name='SALON 23', timeslot_id=NULL, updated_at=NOW() WHERE course_id='60' AND group_id='6APRG';  
UPDATE course_assignment SET teacher_id='48LFGD', room_name='SALON 23', timeslot_id=NULL, updated_at=NOW() WHERE course_id='61' AND group_id='6APRG';   

COMMIT;
-- ============================================================================
-- VERIFICATION QUERIES
-- ============================================================================

-- Count summary
SELECT 'Teachers' AS entity, COUNT(*) AS count FROM teacher
UNION ALL
SELECT 'Teacher Qualifications', COUNT(*) FROM teacher_qualification
UNION ALL
SELECT 'Teacher Availability Hours', COUNT(*) FROM teacher_availability
UNION ALL
SELECT 'Courses', COUNT(*) FROM course
UNION ALL
SELECT 'Rooms', COUNT(*) FROM room
UNION ALL
SELECT 'Timeslots', COUNT(*) FROM timeslot
UNION ALL
SELECT 'Student Groups', COUNT(*) FROM student_group
UNION ALL
SELECT 'Group-Course Relationships', COUNT(*) FROM group_course
UNION ALL
SELECT 'Course Assignments', COUNT(*) FROM course_assignment;

-- Sample data verification
SELECT 'Sample Teachers:' AS info;
SELECT id, name, max_hours_per_week FROM teacher LIMIT 5;

SELECT 'Sample Courses:' AS info;
SELECT id, name, room_requirement, required_hours_per_week FROM course LIMIT 5;

SELECT 'Sample Assignments (unassigned):' AS info;
SELECT ca.id, sg.name AS group_name, c.name AS course_name, ca.sequence_index
FROM course_assignment ca
JOIN student_group sg ON ca.group_id = sg.id
JOIN course c ON ca.course_id = c.id
LIMIT 10;