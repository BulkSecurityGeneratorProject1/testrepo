package com.test.app.repository;

import java.util.List;

import com.test.app.domain.Doctor;
import com.test.app.domain.HospitalDoctorConsultaion;

import org.joda.time.LocalDate;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the HospitalDoctorDTO4 entity.
 */
public interface HospitalDoctorConsultaionRepository extends MongoRepository<HospitalDoctorConsultaion,String> {

	List<HospitalDoctorConsultaion> findBySpecialityOrLocation(String speciality, String location);

	List<HospitalDoctorConsultaion> findBySpecialityAndLocation(String speciality, String location);
	List<HospitalDoctorConsultaion> findBySpecialityAndLocationAndDate(String speciality, String location, LocalDate date);
	List<HospitalDoctorConsultaion> findByLocationAndDate(String location, LocalDate date);
	List<HospitalDoctorConsultaion> findByDoctorName(String doctorName);
	
}