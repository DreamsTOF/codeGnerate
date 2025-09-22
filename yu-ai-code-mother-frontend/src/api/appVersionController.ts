// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 对比两个应用的版本差异 POST /appVersion/compare */
export async function compareAppVersions(
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

/** 根据 id 获取版本详情 (包含代码内容) GET /appVersion/get/vo */
export async function getAppVersionVoById(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getAppVersionVOByIdParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseAppVersionVO>('/appVersion/get/vo', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 分页获取版本列表 (不含代码内容) /appVersion/list/page */
export async function listAppVersionByPage(
  body: API.AppVersionQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageAppVersionQueryVO>('/appVersion/list/page', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
