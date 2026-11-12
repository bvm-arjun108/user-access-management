INSERT INTO pharos_uam.employee(employee_id,employment_status,termination_date)
VALUES (:employee,'TERMINATED',CAST(:date AS date))
ON CONFLICT(employee_id) DO UPDATE SET employment_status='TERMINATED',
termination_date=COALESCE(EXCLUDED.termination_date,employee.termination_date), updated_at=clock_timestamp()
