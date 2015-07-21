#!/usr/bin/env python

import psycopg2, multiprocessing

conn = psycopg2.connect("dbname=MIMIC2 user=rsuvorov password=pwd")
main_cursor = conn.cursor()

# GET_LONGEST_ADMISSIONS = '''select icd.subject_id as subject_id, icd.hadm_id as hadm_id, code as code, description as description, adm_dur.dur as duration
# from mimic2v26.icd9 as icd, (
#     select hadm_id, (disch_dt - admit_dt) as dur
#     from mimic2v26.admissions
#     order by dur desc
#     limit 5 ) as adm_dur
# where icd.hadm_id = adm_dur.hadm_id and icd.sequence <= 3
# order by adm_dur.dur desc, icd.sequence asc'''

# get 10 diseases that permanently appear as most probably diagnoses
# and sort them so that most empirically representative appear first
# by desceding of rate = avg(duration) * ln(amount of admissions) * ln(average age of patients) 
GET_MOST_LONGEST_POPULAR_IMPORTANT_DISEASES = '''
select code as code, avg(adm_dur.dur) as duration, count(adm_dur.*) as cnt, avg(icd.sequence) as avg_seq, avg(admit_dt - p.dob) as avg_age, (avg(adm_dur.dur) * ln(count(adm_dur.*)) * ln(extract(epoch from avg(admit_dt - p.dob)))) as rate 
from mimic2v26.icd9 as icd,
    (select hadm_id, admit_dt, (disch_dt - admit_dt) as dur
    from mimic2v26.admissions ) as adm_dur,
    mimic2v26.d_patients as p
where icd.hadm_id = adm_dur.hadm_id and icd.subject_id = p.subject_id and (admit_dt - p.dob >= '1 day') and icd.sequence <= 3
group by code
order by rate desc
limit 10'''

# get all subjects and their last admissions on the specified disease 
GET_LAST_ADMISSIONS_ON_DISEASE = '''
select ad.subject_id, ad.hadm_id, last_ad_dt.max_admit_dt
from mimic2v26.admissions as ad,
    (select icd.subject_id, max(ad.admit_dt) as max_admit_dt
    from mimic2v26.icd9 as icd, mimic2v26.admissions as ad
    where code = '%s' and icd.hadm_id = ad.hadm_id
    group by icd.subject_id) last_ad_dt
where ad.admit_dt = last_ad_dt.max_admit_dt and ad.subject_id = last_ad_dt.subject_id'''

GET_DIAGNOSES_FOR_PERSON_LAST_YEAR = '''
select distinct icd.code
from mimic2v26.icd9 as icd, mimic2v26.admissions as ad
where icd.hadm_id = ad.hadm_id
    and icd.subject_id = '%(subject_id)s'
    and ad.admit_dt <= (date '%(last_admit_dt)s' + interval '1 day')
    and ad.admit_dt >= (date '%(last_admit_dt)s' - interval '1 year')
    and icd.sequence <= 3
'''

GET_LABITEMS_FOR_PERSON = '''
select distinct le.itemid, li.test_name
from mimic2v26.labevents as le, mimic2v26.d_labitems as li
where le.subject_id = '%(subject_id)s' 
    and le.charttime <= (date '%(last_admit_dt)s' + interval '1 day')
    and le.charttime >= (date '%(last_admit_dt)s' - interval '1 year')
    and le.itemid = li.itemid
'''

GET_LAST_LAB_VALUE = '''
select le.valuenum
from mimic2v26.labevents as le
where le.subject_id = '%(subject_id)s' and le.itemid = '%(labitem_id)s'
    and le.charttime <= (date '%(last_admit_dt)s' + interval '1 day')
    and le.charttime >= (date '%(last_admit_dt)s' - interval '1 year')
order by le.charttime desc
limit 1
'''

COLUMNS_DELIMITER = '\t'
DEFAULT_VALUE = '-'

def get_feature_id(feature_name_id_map, feature_name):
    if feature_name in feature_name_id_map:
        return feature_name_id_map[feature_name]
    else:
        new_id = len(feature_name_id_map)
        feature_name_id_map[feature_name] = new_id
        return new_id
def map_features_to_vector(feature_name_id_map, features_map):
    result = [DEFAULT_VALUE for _ in xrange(len(feature_name_id_map))]
    for name, value in features_map.viewitems():
        result[get_feature_id(feature_name_id_map, name)] = str(value) if value else DEFAULT_VALUE
    return result

main_cursor.execute(GET_MOST_LONGEST_POPULAR_IMPORTANT_DISEASES)
diseases_codes = [c[0] for c in main_cursor.fetchall()]
# 998.59

def get_info_on_disease(disease_icd_code):
    conn = psycopg2.connect("dbname=MIMIC2 user=rsuvorov password=pwd")
    main_cursor = conn.cursor()
    main_cursor.execute(GET_LAST_ADMISSIONS_ON_DISEASE % disease_icd_code)
    
    feature_name_id_map = {}
    subjects = []
    subjects_ids = []
    for (subject_id, last_hadm_id, last_admit_dt) in main_cursor.fetchall():
        # 31520;35363;"3423-03-26 00:00:00"
        # 26408;14660;"3267-01-19 00:00:00"

        subjects_ids.append((subject_id, last_hadm_id, last_admit_dt))

        subject_features = {}
        main_dis_name = 'MAIN DISEASE %s' % disease_icd_code
        get_feature_id(feature_name_id_map, main_dis_name)
        subject_features[main_dis_name] = 1
        
        # diagnoses
        features_cursor = conn.cursor()
        features_cursor.execute(GET_DIAGNOSES_FOR_PERSON_LAST_YEAR % { 'subject_id' : subject_id, 'last_admit_dt' : last_admit_dt })
        for (other_icd,) in features_cursor.fetchall():
            if other_icd == disease_icd_code:
                continue
            dis_name = 'DISEASE %s' % other_icd
            get_feature_id(feature_name_id_map, dis_name)
            subject_features[dis_name] = 1

        # measurements
        labitems_cursor = conn.cursor()
        labitems_cursor.execute(GET_LABITEMS_FOR_PERSON % { 'subject_id' : subject_id, 'last_admit_dt' : last_admit_dt })
        measurements = labitems_cursor.fetchall()
        for (labitem_id, labitem_name) in measurements:
            # 50112
            features_cursor.execute(GET_LAST_LAB_VALUE % { 'subject_id' : subject_id, 'labitem_id' : labitem_id, 'last_admit_dt' : last_admit_dt })
            (last_value,) = features_cursor.fetchone()
            if last_value:
                get_feature_id(feature_name_id_map, labitem_name)
                subject_features[labitem_name] = last_value

        if measurements:
            subjects.append(subject_features)

    with open('%s_data.txt' % disease_icd_code, 'w') as f:
        for subj in subjects:
            print >> f, COLUMNS_DELIMITER.join(map_features_to_vector(feature_name_id_map, subj))
    with open('%s_features.txt' % disease_icd_code, 'w') as f:
        for feat_name, feat_id in sorted(feature_name_id_map.viewitems(), key = lambda p: p[1]):
            print >> f, '%s\t%s' % (feat_id, feat_name)
    with open('%s_subjects.txt' % disease_icd_code, 'w') as f:
        for subj_info in subjects_ids:
            print >> f, COLUMNS_DELIMITER.join(map(str, subj_info))
    

pool = multiprocessing.Pool(4)
pool.map(get_info_on_disease, diseases_codes)
pool.close()
pool.join()
