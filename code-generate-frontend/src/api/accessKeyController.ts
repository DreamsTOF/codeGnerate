// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /accessKey/cdKey */
export async function getCdKey(options?: { [key: string]: any }) {
  return request<API.BaseResponseString>('/accessKey/cdKey', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /accessKey/getInfo */
export async function getApiKey(options?: { [key: string]: any }) {
  return request<API.BaseResponseAccessKey>('/accessKey/getInfo', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 PUT /accessKey/useCdKey */
export async function useCdKey(options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/accessKey/useCdKey', {
    method: 'PUT',
    ...(options || {}),
  })
}
