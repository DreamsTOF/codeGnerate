// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /appVersion/compare */
export async function compare(
  body: API.AppVersionCompareRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseAppVersionCompareVO>('/appVersion/compare', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /appVersion/delete */
export async function deleteById(body: API.DeleteRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/appVersion/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /appVersion/getInfo/${param0} */
export async function getInfo(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getInfoParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params
  return request<API.BaseResponseAppVersionVO>(`/appVersion/getInfo/${param0}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /appVersion/list */
export async function list(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageAppVersionQueryVO>('/appVersion/list', {
    method: 'GET',
    params: {
      ...params,
      appVersionQueryRequest: undefined,
      ...params['appVersionQueryRequest'],
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /appVersion/restore */
export async function restore(
  body: API.AppVersionRestoreRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/appVersion/restore', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /appVersion/save */
export async function save(body: API.AppVersionSaveRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseLong>('/appVersion/save', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
